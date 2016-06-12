package org.mozartoz.truffle.nodes.pattern;

import org.mozartoz.truffle.nodes.DerefIfBoundNodeGen;
import org.mozartoz.truffle.nodes.OzNode;
import org.mozartoz.truffle.runtime.OzFuture;
import org.mozartoz.truffle.runtime.OzVar;

import com.oracle.truffle.api.dsl.CreateCast;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;

@NodeChildren({ @NodeChild("var"), @NodeChild("value") })
public abstract class PatternMatchCaptureNode extends OzNode {

	@CreateCast("value")
	protected OzNode derefValue(OzNode value) {
		return DerefIfBoundNodeGen.create(value);
	}

	public abstract OzNode getVar();

	public abstract OzNode getValue();

	@Specialization(guards = "!isVariable(value)")
	Object capture(OzVar var, Object value) {
		var.bind(value);
		return unit;
	}

	@Specialization(guards = "!isBound(value)")
	Object capture(OzVar var, OzVar value) {
		var.link(value);
		return unit;
	}

	@Specialization(guards = "!isBound(future)")
	Object capture(OzVar var, OzFuture future) {
		var.link(future);
		return unit;
	}

}
