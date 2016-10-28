package org.mozartoz.truffle.runtime;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.mozartoz.truffle.Options;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.coro.Coroutine;
import com.oracle.truffle.coro.CoroutineLocal;

public class OzThread implements Runnable {

	private static final CoroutineLocal<OzThread> CURRENT_OZ_THREAD = new CoroutineLocal<>();

	public static final OzThread MAIN_THREAD = new OzThread();

	public static final Map<OzThread, OzBacktrace> BACKTRACES = Options.STACKTRACE_ON_INTERRUPT ? new ConcurrentHashMap<>() : null;

	private static long threadsCreated = 1L;
	private static long threadsRunnable = 1L;

	public static long getNumberOfThreadsRunnable() {
		return threadsRunnable;
	}

	public static long getNumberOfThreadsCreated() {
		return threadsCreated;
	}

	public static OzThread getCurrent() {
		return CURRENT_OZ_THREAD.get();
	}

	private final Coroutine coroutine;
	private final OzProc proc;

	private String status = "runnable";

	private OzThread() {
		coroutine = (Coroutine) Coroutine.current();
		proc = null;
		setInitialOzThread();
	}

	public OzThread(OzProc proc) {
		this.proc = proc;
		this.coroutine = new Coroutine(this, 1024 * 1024); // 256 seems OK if we parse outside the coro
		threadsCreated++;
	}

	private void setInitialOzThread() {
		CURRENT_OZ_THREAD.set(this);
	}

	public OzProc getProc() {
		return proc;
	}

	public Coroutine getCoroutine() {
		return coroutine;
	}

	public String getStatus() {
		return status;
	}

	@Override
	public void run() {
		setInitialOzThread();
		threadsRunnable++;
		try {
			proc.rootCall("Thread.create");
		} finally {
			threadsRunnable--;
			status = "terminated";
			if (Options.STACKTRACE_ON_INTERRUPT) {
				BACKTRACES.remove(this);
			}
		}
	}

	@TruffleBoundary
	public void yield(Node currentNode) {
		status = "blocked";
		if (Options.STACKTRACE_ON_INTERRUPT) {
			BACKTRACES.put(this, OzBacktrace.capture(currentNode));
		}
		Coroutine.yield();
		status = "runnable";
	}

	public void suspend(Node currentNode) {
		threadsRunnable--;
		yield(currentNode);
		threadsRunnable++;
	}

}
