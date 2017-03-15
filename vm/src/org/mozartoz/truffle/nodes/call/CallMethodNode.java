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

import com.oracle.truffle.api.CompilerDirectives;
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
import com.oracle.truffle.api.profiles.BranchProfile;

@NodeChildren({ @NodeChild("object"), @NodeChild("arguments") })
public abstract class CallMethodNode extends OzNode {

	static final OzUniqueName ooMeth = OzUniqueName.get("ooMeth");
	static final String OTHERWISE = "otherwise";
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
		Object name = labelNode.executeLabel(derefNode.executeDeref(args[0]));
		assert OzGuards.isLiteral(name);

		Lookup lookup = lookupNode.executeLookup(self, name);
		return dispatchNode.executeDispatch(frame, self, lookup, args);
	}

	@ImportStatic(CallMethodNode.class)
	@NodeChildren({ @NodeChild("self"), @NodeChild("name") })
	public abstract static class MethodLookupNode extends OzNode {

		public static MethodLookupNode create() {
			return MethodLookupNodeGen.create(null, null);
		}

		public abstract Lookup executeLookup(OzObject self, Object name);

		@Specialization(guards = {
				"self.getClazz() == cachedClazz",
				"name == cachedName",
		}, limit = "methodCache(3)")
		protected Lookup descriptorIdentityLookup(OzObject self, Object name,
				@Cached("self.getClazz()") DynamicObject cachedClazz,
				@Cached("name") Object cachedName,
				@Cached("genericLookup(self, name)") Lookup cachedLookup) {
			return cachedLookup;
		}

		BranchProfile otherwiseProfile = BranchProfile.create();

		@Specialization(contains = "descriptorIdentityLookup")
		protected Lookup genericLookup(OzObject self, Object name) {
			OzProc proc = getMethod(self, name);
			if (proc == null) {
				otherwiseProfile.enter();
				proc = getMethod(self, OTHERWISE);
				if (proc == null) {
					CompilerDirectives.transferToInterpreterAndInvalidate();
					throw new RuntimeException(name + " not found");
				}
				return new Lookup(proc, true);
			}
			return new Lookup(proc, false);
		}

		@TruffleBoundary
		protected static OzProc getMethod(OzObject self, Object name) {
			DynamicObject methods = (DynamicObject) self.getClazz().get(ooMeth);
			return (OzProc) methods.get(name);
		}

	}

	public static class Lookup {
		public final OzProc proc;
		public final boolean embed;

		private Lookup(OzProc proc, boolean embed) {
			this.proc = proc;
			this.embed = embed;
		}

	}

	@ImportStatic(CallMethodNode.class)
	@NodeChildren({ @NodeChild("self"), @NodeChild("lookup"), @NodeChild("args") })
	public abstract static class MethodDispatchNode extends OzNode {

		public static MethodDispatchNode create() {
			return MethodDispatchNodeGen.create(null, null, null);
		}

		public abstract Object executeDispatch(VirtualFrame frame, OzObject self, Lookup lookup, Object[] args);

		@Specialization(guards = {
				"lookup.proc == cachedLookup.proc",
				"!lookup.embed",
				"!cachedLookup.embed"
		}, limit = "methodCache(3)")
		protected Object dispatchCached(VirtualFrame frame, OzObject self, Lookup lookup, Object[] args,
				@Cached("lookup") Lookup cachedLookup,
				@Cached("createDirectCallNode(cachedLookup.proc.callTarget)") DirectCallNode callNode) {
			Object[] arguments = new Object[] { self, args[0] };
			return callNode.call(frame, OzArguments.pack(cachedLookup.proc.declarationFrame, arguments));
		}

		@Specialization(guards = {
				"lookup.proc == cachedLookup.proc",
				"lookup.embed",
				"cachedLookup.embed"
		}, limit = "methodCache(3)")
		protected Object dispatchCachedOtherwise(VirtualFrame frame, OzObject self, Lookup lookup, Object[] args,
				@Cached("lookup") Lookup cachedLookup,
				@Cached("createDirectCallNode(cachedLookup.proc.callTarget)") DirectCallNode callNode) {
			Object[] arguments = new Object[] { self, OTHERWISE_MESSAGE_FACTORY.newRecord(args[0]) };
			return callNode.call(frame, OzArguments.pack(cachedLookup.proc.declarationFrame, arguments));
		}

		BranchProfile otherwiseProfile = BranchProfile.create();

		@Specialization(contains = { "dispatchCached", "dispatchCachedOtherwise" })
		protected Object dispatchGeneric(VirtualFrame frame, OzObject self, Lookup lookup, Object[] args,
				@Cached("create()") IndirectCallNode callNode) {
			Object message = args[0];
			if (lookup.embed) {
				otherwiseProfile.enter();
				message = OTHERWISE_MESSAGE_FACTORY.newRecord(message);
			}
			Object[] arguments = new Object[] { self, message };
			return callNode.call(frame, lookup.proc.callTarget,
					OzArguments.pack(lookup.proc.declarationFrame, arguments));
		}

		protected static DirectCallNode createDirectCallNode(RootCallTarget callTarget) {
			return CallProcNode.createDirectCallNode(callTarget);
		}

	}

}
