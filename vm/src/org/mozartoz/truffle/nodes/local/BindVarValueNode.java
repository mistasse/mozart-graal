package org.mozartoz.truffle.nodes.local;

import org.mozartoz.truffle.Options;
import org.mozartoz.truffle.nodes.OzNode;
import org.mozartoz.truffle.nodes.local.BindVarValueNodeGen.PrintLinksNodeGen;
import org.mozartoz.truffle.runtime.OzVar;
import org.mozartoz.truffle.runtime.Variable;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;

abstract class AbstractBindVarValueNode extends OzNode {
	public static AbstractBindVarValueNode create() {
		if (Options.PRINT_NLINKS != null) {
			return PrintLinksNodeGen.create(null, null);
		}
		return BindVarValueNode.create();
	}

	public abstract Object executeBindVarValue(Variable var, Object value);
}

@NodeChildren({ @NodeChild("var"), @NodeChild("value") })
public abstract class BindVarValueNode extends AbstractBindVarValueNode {

	public static BindVarValueNode create() {
		return BindVarValueNodeGen.create(null, null);
	}

	public abstract Object executeBindVarValue(Variable var, Object value);

	@Specialization(guards = "var.getNext() == var")
	Object bind1(Variable var, Object value) {
		var.setInternalValueAndUnlink(value, var);
		return value;
	}

	@Specialization(guards = { "var.getNext() != var", "var.getNext().getNext() == var" })
	Object bind2(Variable var, Object value) {
		var.setInternalValue(value, var);
		var.getNextAndUnlink().setInternalValueAndUnlink(value, var);
		return value;
	}

	@Specialization(guards = { "var.getNext() != var", "var.getNext().getNext().getNext() == var" })
	Object bind3(Variable var, Object value) {
		var.setInternalValue(value, var);
		Variable next = var.getNextAndUnlink();
		next.setInternalValue(value, var);
		next.getNextAndUnlink().setInternalValueAndUnlink(value, var);
		return value;
	}

	@Specialization(contains = { "bind1", "bind2", "bind3" })
	Object bindLeft(OzVar var, Object value) {
		var.bind(value);
		return value;
	}

	@NodeChildren({ @NodeChild("var"), @NodeChild("value") })
	public static abstract class PrintLinksNode extends AbstractBindVarValueNode {

		@Specialization
		public Object bind(Variable var, Object value,
				@Cached("create()") BindVarValueNode bindNode) {
			int count = 1;
			Variable current = var.getNext();
			while (current != var) {
				count += 1;
				current = current.getNext();
			}
			System.out.println(Options.PRINT_NLINKS + " --- " + count);
			return bindNode.executeBindVarValue(var, value);
		}
	}

}
