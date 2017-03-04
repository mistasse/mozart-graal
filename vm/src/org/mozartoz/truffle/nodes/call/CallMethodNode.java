package org.mozartoz.truffle.nodes.call;

import org.mozartoz.truffle.Options;
import org.mozartoz.truffle.nodes.DerefNode;
import org.mozartoz.truffle.nodes.OzGuards;
import org.mozartoz.truffle.nodes.OzNode;
import org.mozartoz.truffle.nodes.builtins.RecordBuiltins.LabelNode;
import org.mozartoz.truffle.runtime.Arity;
import org.mozartoz.truffle.runtime.OzArguments;
import org.mozartoz.truffle.runtime.OzObject;
import org.mozartoz.truffle.runtime.OzProc;
import org.mozartoz.truffle.runtime.OzUniqueName;
import org.mozartoz.truffle.runtime.RecordFactory;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
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

	protected static final int lim(int i) {
		return Options.OPTIMIZE_METHOD_CALLS ? i : 0;
	}

	@Child LabelNode labelNode = LabelNode.create();
	@Child DerefNode derefNode = DerefNode.create();

	/** Must only be used by CallNode */
	public static CallMethodNode create() {
		return CallMethodNodeGen.create(null, null);
	}

	abstract Object executeCall(VirtualFrame frame, OzObject self, Object[] args);

	static final RecordFactory OTHERWISE_MESSAGE_FACTORY = Arity.build("otherwise", 1L).createFactory();

	@Specialization(guards = {
			"cachedProc != null",
			"cachedProc == methodFromArgs(self, args, derefNode, labelNode)"
	}, limit = "lim(1)")
	protected Object callProcIdentity(VirtualFrame frame, OzObject self, Object[] args,
			@Cached("methodFromArgs(self, args, derefNode, labelNode)") OzProc cachedProc,
			@Cached("createDirectCallNode(cachedProc.callTarget)") DirectCallNode callNode) {
		assert args.length == 1;
		Object[] arguments = new Object[] { self, args[0] };
		return callNode.call(frame, OzArguments.pack(cachedProc.declarationFrame, arguments));
	}

	@Specialization(guards = {
			"cachedCallTarget != null",
			"callTargetFromArgs(self, args, derefNode, labelNode) == cachedCallTarget"
	}, contains = "callProcIdentity", limit = "lim(3)")
	protected Object callDirect(VirtualFrame frame, OzObject self, Object[] args,
			@Cached("callTargetFromArgs(self, args, derefNode, labelNode)") RootCallTarget cachedCallTarget,
			@Cached("createDirectCallNode(cachedCallTarget)") DirectCallNode callNode) {
		assert args.length == 1;
		OzProc proc = methodFromArgs(self, args, derefNode, labelNode);
		Object[] arguments = new Object[] { self, args[0] };
		return callNode.call(frame, OzArguments.pack(proc.declarationFrame, arguments));
	}

	@Specialization(contains = { "callProcIdentity", "callDirect" })
	protected Object callObject(VirtualFrame frame, OzObject self, Object[] args,
			@Cached("create()") IndirectCallNode callNode,
			@Cached("createBinaryProfile()") ConditionProfile otherwise) {
		assert args.length == 1;
		Object message = derefNode.executeDeref(args[0]);

		final Object name = labelNode.executeLabel(message);
		assert OzGuards.isLiteral(name);

		OzProc method = getMethod(self, name);
		if (otherwise.profile(method == null)) { // redirect to otherwise
			method = getMethod(self, "otherwise");
			message = OTHERWISE_MESSAGE_FACTORY.newRecord(message);
		}

		Object[] arguments = new Object[] { self, message };
		return callNode.call(frame, method.callTarget, OzArguments.pack(method.declarationFrame, arguments));
	}

	static final OzUniqueName ooMeth = OzUniqueName.get("ooMeth");

	protected RootCallTarget callTargetFromArgs(OzObject self, Object[] args, DerefNode derefNode, LabelNode labelNode) {
		OzProc proc = methodFromArgs(self, args, derefNode, labelNode);
		if (proc == null) {
			return null;
		}
		return proc.callTarget;
	}

	protected OzProc methodFromArgs(OzObject self, Object[] args, DerefNode derefNode, LabelNode labelNode) {
		return getMethod(self, labelNode.executeLabel(derefNode.executeDeref(args[0])));
	}

	@TruffleBoundary
	protected OzProc getMethod(OzObject self, Object name) {
		DynamicObject methods = (DynamicObject) self.getClazz().get(ooMeth);
		return (OzProc) methods.get(name);
	}

	protected DirectCallNode createDirectCallNode(RootCallTarget callTarget) {
		return CallProcNode.createDirectCallNode(callTarget);
	}

}
