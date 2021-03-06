package org.mozartoz.truffle.runtime;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;

@SuppressWarnings("serial")
public class OzException extends RuntimeException {

	static final RecordFactory ERROR_ARITY = Arity.build("error", 1L, "debug").createFactory();
	static final RecordFactory SYSTEM_ARITY = Arity.build("system", 1L, "debug").createFactory();
	static final RecordFactory FAILURE_ARITY = Arity.build("failure", "debug").createFactory();

	public static DynamicObject newError(Object error) {
		return ERROR_ARITY.newRecord(error, Unit.INSTANCE);
	}

	public static DynamicObject newSystemError(Object error) {
		return SYSTEM_ARITY.newRecord(error, Unit.INSTANCE);
	}

	public static DynamicObject newFailure() {
		return FAILURE_ARITY.newRecord(Unit.INSTANCE);
	}

	private final Object data;

	public OzException(Node currentNode, String message) {
		this(currentNode, newError(message.intern()));
	}

	public OzException(Node currentNode, Object data) {
		super(data.toString());
		OzBacktrace backtrace = OzBacktrace.capture(currentNode);

		Object storedData = data;
		if (data instanceof DynamicObject) {
			DynamicObject dataRecord = (DynamicObject) data;
			boolean hasDebug = dataRecord.containsKey("debug");
			if (hasDebug && dataRecord.get("debug") == Unit.INSTANCE) {
				DynamicObject dataWithDebug = dataRecord.copy(dataRecord.getShape());
				dataWithDebug.getShape().getProperty("debug").setInternal(dataWithDebug, backtrace);
				storedData = dataWithDebug;
			}
		}

		this.data = storedData;
	}

	public Object getData() {
		return data;
	}

	public OzBacktrace getBacktrace() {
		if (data instanceof DynamicObject) {
			Object debug = ((DynamicObject) data).get("debug");
			return (OzBacktrace) debug;
		} else {
			return null;
		}
	}

}
