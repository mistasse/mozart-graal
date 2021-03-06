package org.mozartoz.truffle.nodes.pattern;

import org.mozartoz.truffle.nodes.DerefNode;
import org.mozartoz.truffle.nodes.OzNode;
import org.mozartoz.truffle.runtime.OzCons;

import com.oracle.truffle.api.dsl.CreateCast;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;

@NodeChild("value")
public abstract class PatternMatchConsNode extends OzNode {

	@CreateCast("value")
	protected OzNode derefValue(OzNode value) {
		return DerefNode.create(value);
	}

	@Specialization
	boolean patternMatch(OzCons cons) {
		return true;
	}

	@Specialization(guards = "!isCons(object)")
	boolean patternMatch(Object object) {
		return false;
	}

}
