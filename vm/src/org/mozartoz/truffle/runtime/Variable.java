package org.mozartoz.truffle.runtime;

import org.mozartoz.truffle.nodes.OzNode;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.SourceSection;

public abstract class Variable {

	private @CompilationFinal Object value = null;
	private boolean needed = false;

	/** A circular list of linked Variable */
	private Variable next = this;

	protected SourceSection declaration;

	public boolean isBound() {
		return value != null;
	}

	public Object getBoundValue(OzNode currentNode) {
		final Object value = this.value;
		assert value != null : "unbound var";
		return value;
	}

	public void link(Variable other) {
		assert !isBound();
		assert !other.isBound();
		assert !isLinkedTo(other);

		// Link both circular lists
		Variable oldNext = this.next;
		this.next = other.next;
		other.next = oldNext;
	}

	public boolean isLinkedTo(Variable other) {
		Variable var = this;
		do {
			if (var == other) {
				return true;
			}
			var = var.next;
		} while (var != this);
		return false;
	}

	public Variable getNext() {
		return next;
	}

	public void setInternalValue(Object value, Variable from) {
		assert !isBound();
		assert !(value instanceof Variable);
		assert !(this instanceof OzFuture) || from instanceof OzFuture;
		this.value = value;
	}

	public void unlink() {
		this.next = null;
	}

	public Variable getNextAndUnlink() {
		Variable next = this.next;
		this.unlink();
		return next;
	}

	public void setInternalValueAndUnlink(Object value, Variable from) {
		assert !isBound();
		assert !(value instanceof Variable);
		assert !(this instanceof OzFuture) || from instanceof OzFuture;
		this.value = value;
		this.unlink();
	}

	public void bind(Object value) {
		setInternalValue(value, this);

		Variable var = getNextAndUnlink();
		while (var != this) {
			var.setInternalValue(value, this);
			
			var = var.getNextAndUnlink();
		}
	}

	public boolean isNeeded() {
		Variable var = this;
		do {
			if (var.needed) {
				return true;
			}
			var = var.next;
		} while (var != this);
		return false;
	}

	public void makeNeeded() {
		this.needed = true;
	}

	public Object waitValue(OzNode currentNode) {
		makeNeeded();
		return waitValueQuiet(currentNode);
	}

	@TruffleBoundary
	public Object waitValueQuiet(OzNode currentNode) {
		assert !isBound();
		while (!isBound()) {
			OzThread.getCurrent().suspend(currentNode);
		}
		return getBoundValue(currentNode);
	}

	@Override
	public abstract String toString();

}
