package org.mozartoz.truffle.nodes.builtins;

import static org.mozartoz.truffle.nodes.builtins.Builtin.ALL;

import java.math.BigInteger;

import org.mozartoz.truffle.nodes.OzNode;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.ExactMath;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;

public abstract class IntBuiltins {

	@Builtin(name = "is", deref = ALL)
	@GenerateNodeFactory
	@NodeChild("value")
	public static abstract class IsIntNode extends OzNode {

		@Specialization
		boolean isInt(long value) {
			return true;
		}

		@Specialization
		boolean isInt(BigInteger value) {
			return true;
		}

		@Specialization(guards = { "!isLong(value)", "!isBigInteger(value)" })
		boolean isInt(Object value) {
			return false;
		}

	}

	@Builtin(name = "toFloat", deref = ALL)
	@GenerateNodeFactory
	@NodeChild("value")
	public static abstract class ToFloatNode extends OzNode {

		@Specialization
		double toFloat(long value) {
			return (double) value;
		}

		@TruffleBoundary
		@Specialization
		double toFloat(BigInteger value) {
			return value.doubleValue();
		}

	}

	@Builtin(deref = ALL)
	@GenerateNodeFactory
	@NodeChildren({ @NodeChild("left"), @NodeChild("right") })
	public static abstract class DivNode extends OzNode {

		@Specialization(rewriteOn = ArithmeticException.class)
		protected long div(long a, long b) {
			return a / b;
		}

		@TruffleBoundary
		@Specialization
		protected BigInteger div(BigInteger a, BigInteger b) {
			return a.divide(b);
		}

	}

	@Builtin(deref = ALL)
	@GenerateNodeFactory
	@NodeChildren({ @NodeChild("left"), @NodeChild("right") })
	public static abstract class ModNode extends OzNode {

		@Specialization(rewriteOn = ArithmeticException.class)
		protected long mod(long a, long b) {
			return a % b;
		}

		@TruffleBoundary
		@Specialization
		protected BigInteger mod(BigInteger a, BigInteger b) {
			return a.mod(b);
		}

	}

	@Builtin(name = "or", deref = ALL)
	@GenerateNodeFactory
	@NodeChildren({ @NodeChild("left"), @NodeChild("right") })
	public static abstract class OrNode extends OzNode {

		@Specialization(rewriteOn = ArithmeticException.class)
		protected long or(long a, long b) {
			return a | b;
		}

		@TruffleBoundary
		@Specialization
		protected BigInteger or(BigInteger a, BigInteger b) {
			return a.or(b);
		}

	}

	@Builtin(name = "xor", deref = ALL)
	@GenerateNodeFactory
	@NodeChildren({ @NodeChild("left"), @NodeChild("right") })
	public static abstract class XorNode extends OzNode {

		@Specialization(rewriteOn = ArithmeticException.class)
		protected long xor(long a, long b) {
			return a ^ b;
		}

		@TruffleBoundary
		@Specialization
		protected BigInteger xor(BigInteger a, BigInteger b) {
			return a.xor(b);
		}

	}

	@Builtin(name = "l<<", deref = ALL)
	@GenerateNodeFactory
	@NodeChildren({ @NodeChild("left"), @NodeChild("right") })
	public static abstract class LoseLShiftNode extends OzNode {

		@Specialization
		protected long llshift(long a, long b) {
			return a << b;
		}

	}

	@Builtin(name = "<<", deref = ALL)
	@GenerateNodeFactory
	@NodeChildren({ @NodeChild("left"), @NodeChild("right") })
	public static abstract class LShiftNode extends OzNode {

		@Specialization(rewriteOn = ArithmeticException.class)
		protected long lshift(long a, long b) {
			return ExactMath.multiplyExact(a, 1 << b);
		}

		@TruffleBoundary
		@Specialization
		protected BigInteger lshift(BigInteger a, long b) {
			return a.shiftLeft((int) b);
		}

	}

}
