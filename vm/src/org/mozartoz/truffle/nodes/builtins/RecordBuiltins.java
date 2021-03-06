package org.mozartoz.truffle.nodes.builtins;

import static org.mozartoz.truffle.nodes.builtins.Builtin.ALL;

import java.util.HashMap;
import java.util.Map;

import org.mozartoz.truffle.nodes.DerefIfBoundNode;
import org.mozartoz.truffle.nodes.DerefIfBoundNodeGen;
import org.mozartoz.truffle.nodes.OzGuards;
import org.mozartoz.truffle.nodes.OzNode;
import org.mozartoz.truffle.nodes.builtins.RecordBuiltinsFactory.IsRecordNodeFactory;
import org.mozartoz.truffle.nodes.builtins.RecordBuiltinsFactory.LabelNodeFactory;
import org.mozartoz.truffle.runtime.Arity;
import org.mozartoz.truffle.runtime.OzCons;
import org.mozartoz.truffle.runtime.OzException;
import org.mozartoz.truffle.runtime.OzFailedValue;
import org.mozartoz.truffle.runtime.OzName;
import org.mozartoz.truffle.runtime.OzRecord;
import org.mozartoz.truffle.runtime.OzThread;
import org.mozartoz.truffle.runtime.OzVar;
import org.mozartoz.truffle.runtime.Variable;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Property;

public abstract class RecordBuiltins {

	@Builtin(name = "is", deref = ALL)
	@GenerateNodeFactory
	@NodeChild("value")
	public static abstract class IsRecordNode extends OzNode {

		public static IsRecordNode create() {
			return IsRecordNodeFactory.create(null);
		}

		public abstract boolean executeIsRecord(Object record);

		@Specialization
		boolean isRecord(String atom) {
			return true;
		}

		@Specialization(guards = "isLiteral(value)")
		boolean isRecordLiteral(Object value) {
			return true;
		}

		@Specialization
		boolean isRecord(OzCons value) {
			return true;
		}

		@Specialization
		boolean isRecord(DynamicObject value) {
			return true;
		}

		@Fallback
		boolean isRecord(Object value) {
			return false;
		}

	}

	@Builtin(deref = ALL)
	@GenerateNodeFactory
	@NodeChild("record")
	public static abstract class LabelNode extends OzNode {

		public static LabelNode create() {
			return LabelNodeFactory.create(null);
		}

		public abstract Object executeLabel(Object record);

		@Specialization
		protected Object label(String atom) {
			return atom;
		}

		@Specialization
		protected Object label(OzName name) {
			return name;
		}

		@Specialization
		protected Object label(OzCons cons) {
			return "|";
		}

		@Specialization
		protected Object label(DynamicObject record) {
			return Arity.LABEL_LOCATION.get(record);
		}

	}

	@Builtin(deref = ALL)
	@GenerateNodeFactory
	@NodeChild("record")
	public static abstract class WidthNode extends OzNode {

		@Specialization
		long width(String atom) {
			return 0;
		}

		@Specialization
		long width(OzName name) {
			return 0;
		}

		@Specialization
		long width(OzCons cons) {
			return 2;
		}

		@Specialization
		long width(DynamicObject record) {
			return OzRecord.getArity(record).getWidth();
		}

	}

	@Builtin(deref = ALL)
	@GenerateNodeFactory
	@NodeChild("record")
	public static abstract class ArityNode extends OzNode {

		@Specialization
		Object arity(String atom) {
			return "nil";
		}

		@Specialization
		Object arity(OzName name) {
			return "nil";
		}

		@Specialization
		Object arity(OzCons cons) {
			return Arity.CONS_ARITY.asOzList();
		}

		@Specialization
		Object arity(DynamicObject record) {
			return OzRecord.getArity(record).asOzList();
		}

	}

	@Builtin(deref = ALL)
	@GenerateNodeFactory
	@NodeChild("record")
	public static abstract class CloneNode extends OzNode {

		@Specialization
		String clone(String atom) {
			return atom;
		}

		@Specialization
		OzCons clone(OzCons cons) {
			return new OzCons(new OzVar(getSourceSection()), new OzVar(getSourceSection()));
		}

		@Specialization
		DynamicObject clone(DynamicObject record) {
			Arity arity = OzRecord.getArity(record);
			Object[] values = new Object[arity.getWidth()];
			for (int i = 0; i < values.length; i++) {
				values[i] = new OzVar(getSourceSection());
			}
			return OzRecord.buildRecord(arity, values);
		}

	}

	@Builtin(deref = ALL)
	@GenerateNodeFactory
	@NodeChild("record")
	public static abstract class WaitOrNode extends OzNode {

		@Child DerefIfBoundNode derefNode = DerefIfBoundNodeGen.create();

		@Specialization
		Object waitOr(DynamicObject record) {
			Iterable<Property> properties = record.getShape().getProperties();
			for (;;) {
				for (Property property : properties) {
					Object value = tryDeref(property.get(record, record.getShape()));
					if (OzGuards.isFailedValue(value)) {
						throw new OzException(this, ((OzFailedValue) value).getData());
					} else if (OzGuards.isVariable(value)) {
						// Need to wait
						((Variable) value).makeNeeded();
					} else {
						return value;
					}
				}

				OzThread.getCurrent().yield(this);
			}
		}

		private Object tryDeref(Object value) {
			return derefNode.executeDerefIfBound(value);
		}

	}

	@Builtin(deref = ALL)
	@GenerateNodeFactory
	@NodeChildren({ @NodeChild("label"), @NodeChild("contents") })
	public static abstract class MakeDynamicNode extends OzNode {

		@Child DerefIfBoundNode derefNode = DerefIfBoundNode.create();

		@Specialization
		protected String makeDynamic(String label, String emptyContents) {
			return label;
		}

		@Specialization
		protected OzName makeDynamic(OzName label, String emptyContents) {
			return label;
		}

		@TruffleBoundary
		@Specialization(guards = "isLiteral(label)")
		protected Object makeDynamic(Object label, DynamicObject contents) {
			int width = OzRecord.getArity(contents).getWidth();
			assert width % 2 == 0;
			int size = width / 2;
			Map<Object, Object> map = new HashMap<Object, Object>(size);

			for (int i = 0; i < size; i++) {
				Object feature = derefNode.executeDerefIfBound(contents.get((long) i * 2 + 1));
				Object value = derefNode.executeDerefIfBound(contents.get((long) i * 2 + 2));
				if (map.containsKey(feature)) {
					throw kernelError("recordConstruction", label, buildPairs(contents));
				}
				map.put(feature, value);
			}

			if (label == "|" && size == 2 && map.containsKey(1L) && map.containsKey(2L)) {
				return new OzCons(map.get(1L), map.get(2L));
			} else {
				return OzRecord.buildRecord(label, map);
			}
		}

		private Object buildPairs(DynamicObject contents) {
			int size = OzRecord.getArity(contents).getWidth() / 2;
			Object list = "nil";
			for (int i = size - 1; i >= 0; i--) {
				Object feature = derefNode.executeDerefIfBound(contents.get((long) i * 2 + 1));
				Object value = derefNode.executeDerefIfBound(contents.get((long) i * 2 + 2));
				list = new OzCons(Arity.PAIR_FACTORY.newRecord(feature, value), list);
			}
			return list;
		}

	}

	@GenerateNodeFactory
	@NodeChildren({ @NodeChild("value"), @NodeChild("patLabel"), @NodeChild("patFeatures") })
	public static abstract class TestNode extends OzNode {

		@Specialization
		Object test(Object value, Object patLabel, Object patFeatures) {
			return unimplemented();
		}

	}

	@GenerateNodeFactory
	@NodeChildren({ @NodeChild("value"), @NodeChild("patLabel") })
	public static abstract class TestLabelNode extends OzNode {

		@Specialization
		Object testLabel(Object value, Object patLabel) {
			return unimplemented();
		}

	}

	@GenerateNodeFactory
	@NodeChildren({ @NodeChild("value"), @NodeChild("patFeature"), @NodeChild("found") })
	public static abstract class TestFeatureNode extends OzNode {

		@Specialization
		Object testFeature(Object value, Object patFeature, OzVar found) {
			return unimplemented();
		}

	}

	@Builtin(deref = { 1, 2 }, tryDeref = 3)
	@GenerateNodeFactory
	@NodeChildren({ @NodeChild("record"), @NodeChild("feature"), @NodeChild("fieldValue"), @NodeChild("result") })
	public static abstract class AdjoinAtIfHasFeatureNode extends OzNode {

		@Specialization
		boolean adjoinAtIfHasFeature(String atom, Object feature, Object fieldValue, OzVar result) {
			assert OzGuards.isFeature(feature);
			return false;
		}

		@Specialization
		boolean adjoinAtIfHasFeature(OzCons cons, Object feature, Object fieldValue, OzVar result) {
			assert OzGuards.isFeature(feature);
			if (feature instanceof Long) {
				if ((long) feature == 1L) {
					result.bind(new OzCons(fieldValue, cons.getTail()));
					return true;
				} else if ((long) feature == 2L) {
					result.bind(new OzCons(cons.getHead(), fieldValue));
					return true;
				}
			}
			return false;
		}

		@Specialization
		boolean adjoinAtIfHasFeature(DynamicObject record, Object feature, Object fieldValue, OzVar result) {
			assert OzGuards.isFeature(feature);
			Property property = record.getShape().getProperty(feature);
			if (property != null) {
				DynamicObject newRecord = record.copy(record.getShape());
				property.setInternal(newRecord, fieldValue); // Internal to avoid the final check
				result.bind(newRecord);
				return true;
			} else {
				return false;
			}
		}

	}

}
