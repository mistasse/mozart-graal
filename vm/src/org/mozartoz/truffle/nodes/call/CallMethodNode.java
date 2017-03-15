package org.mozartoz.truffle.nodes.call;

import org.mozartoz.truffle.Options;
import org.mozartoz.truffle.nodes.DerefNode;
import org.mozartoz.truffle.nodes.OzGuards;
import org.mozartoz.truffle.nodes.OzNode;
import org.mozartoz.truffle.nodes.builtins.RecordBuiltins.LabelNode;
import org.mozartoz.truffle.nodes.call.CallMethodNodeGen.MethodDispatchNodeGen;
import org.mozartoz.truffle.nodes.call.CallMethodNodeGen.MethodLookupNodeGen;
import org.mozartoz.truffle.runtime.Arity;
import org.mozartoz.truffle.runtime.OzArguments;
import org.mozartoz.truffle.runtime.OzObject;
import org.mozartoz.truffle.runtime.OzProc;
import org.mozartoz.truffle.runtime.OzUniqueName;
import org.mozartoz.truffle.runtime.RecordFactory;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.ConditionProfile;

@NodeChildren({ @NodeChild("object"), @NodeChild("arguments") })
public abstract class CallMethodNode extends OzNode {

	public static final String OTHERWISE = "otherwise";
	static final OzUniqueName ooMeth = OzUniqueName.get("ooMeth");
	static final RecordFactory OTHERWISE_MESSAGE_FACTORY = Arity.build("otherwise", 1L).createFactory();

	public static int methodCache(int l) {
		return Options.OPTIMIZE_METHODS ? l : 0;
	}

	/** Must only be used by CallNode */
	public static CallMethodNode create() {
		return CallMethodNodeGen.create(null, null);
	}

	abstract Object executeCall(VirtualFrame frame, OzObject self, Object[] args);

	@Specialization
	public Object callMethod(VirtualFrame frame, OzObject self, Object[] args,
			@Cached("create()") DerefNode derefNode,
			@Cached("create()") LabelNode labelNode,
			@Cached("create()") MethodLookupNode lookupNode,
			@Cached("create()") MethodDispatchNode dispatchNode) {
		assert args.length == 1;
		Object message = args[0];
		Object name = labelNode.executeLabel(derefNode.executeDeref(message));
		assert OzGuards.isLiteral(name);

		OzProc lookup = lookupNode.executeLookup(self, name);
		return dispatchNode.executeDispatch(frame, self, lookup, message);
	}

	@ImportStatic(CallMethodNode.class)
	@NodeChildren({ @NodeChild("self"), @NodeChild("name") })
	public abstract static class MethodLookupNode extends OzNode {

		public static MethodLookupNode create() {
			return MethodLookupNodeGen.create(null, null);
		}

		public abstract OzProc executeLookup(OzObject self, Object name);

		@Specialization(guards = {
				"self.getClazz() == cachedClazz",
				"name == cachedName",
		}, limit = "methodCache(3)")
		protected OzProc descriptorIdentityLookup(OzObject self, Object name,
				@Cached("self.getClazz()") DynamicObject cachedClazz,
				@Cached("name") Object cachedName,
				@Cached("genericLookup(self, name)") OzProc cachedLookup) {
			return cachedLookup;
		}

		@Specialization(contains = "descriptorIdentityLookup")
		protected OzProc genericLookup(OzObject self, Object name) {
			return getMethod(self, name);
		}

		@TruffleBoundary
		protected static OzProc getMethod(OzObject self, Object name) {
			DynamicObject methods = (DynamicObject) self.getClazz().get(ooMeth);
			return (OzProc) methods.get(name);
		}

	}

	@ImportStatic(CallMethodNode.class)
	@NodeChildren({ @NodeChild("self"), @NodeChild("lookup"), @NodeChild("args") })
	public abstract static class MethodDispatchNode extends OzNode {

		@Child MethodLookupNode otherwiseLookupNode = MethodLookupNode.create();

		public static MethodDispatchNode create() {
			return MethodDispatchNodeGen.create(null, null, null);
		}

		public abstract Object executeDispatch(VirtualFrame frame, OzObject self, OzProc lookup, Object message);

		@Specialization(guards = {
				"lookup == cachedLookup",
				"cachedLookup != null",
		}, limit = "methodCache(3)")
		protected Object dispatchCached(VirtualFrame frame, OzObject self, OzProc lookup, Object message,
				@Cached("lookup") OzProc cachedLookup,
				@Cached("createDirectCallNode(cachedLookup.callTarget)") DirectCallNode callNode) {
			Object[] arguments = new Object[] { self, message };
			return callNode.call(frame, OzArguments.pack(cachedLookup.declarationFrame, arguments));
		}

		@Specialization(guards = {
				"lookup == null",
				"otherwiseLookupNode.executeLookup(self, OTHERWISE) == otherwiseCachedLookup",
				"otherwiseCachedLookup != null",
		}, limit = "methodCache(3)")
		protected Object dispatchCachedOtherwise(VirtualFrame frame, OzObject self, Object lookup, Object message,
				@Cached("otherwiseLookupNode.executeLookup(self, OTHERWISE)") OzProc otherwiseCachedLookup,
				@Cached("createDirectCallNode(otherwiseCachedLookup.callTarget)") DirectCallNode callNode) {
			Object[] arguments = new Object[] { self, OTHERWISE_MESSAGE_FACTORY.newRecord(message) };
			return callNode.call(frame, OzArguments.pack(otherwiseCachedLookup.declarationFrame, arguments));
		}

		ConditionProfile otherwiseProfile = ConditionProfile.createCountingProfile();

		@Specialization(contains = { "dispatchCached", "dispatchCachedOtherwise" })
		protected Object dispatchGeneric(VirtualFrame frame, OzObject self, Object lookup, Object message,
				@Cached("create()") IndirectCallNode callNode) {
			if (otherwiseProfile.profile(lookup == null)) {
				lookup = otherwiseLookupNode.executeLookup(self, OTHERWISE);
				message = OTHERWISE_MESSAGE_FACTORY.newRecord(message);
			}
			Object[] arguments = new Object[] { self, message };
			OzProc proc = (OzProc) lookup;
			return callNode.call(frame, proc.callTarget,
					OzArguments.pack(proc.declarationFrame, arguments));
		}

		protected static DirectCallNode createDirectCallNode(RootCallTarget callTarget) {
			return CallProcNode.createDirectCallNode(callTarget);
		}

	}

}
