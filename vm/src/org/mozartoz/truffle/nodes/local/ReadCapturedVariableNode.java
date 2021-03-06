package org.mozartoz.truffle.nodes.local;

import org.mozartoz.truffle.Options;
import org.mozartoz.truffle.nodes.OzNode;
import org.mozartoz.truffle.runtime.OzArguments;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class ReadCapturedVariableNode extends OzNode {
	protected static final boolean CACHE_READ = Options.CACHE_READ;

	public abstract Object executeRead(VirtualFrame frame);

	final FrameSlot slot;
	final int depth;

	public ReadCapturedVariableNode(FrameSlot slot, int depth) {
		this.slot = slot;
		this.depth = depth;
	}

	@Specialization(guards = "CACHE_READ")
	protected Object readWithCache(VirtualFrame frame,
			@Cached("createCachingReadNode(slot)") CachingReadFrameSlotNode readNode) {
		Frame parentFrame = OzArguments.getParentFrame(frame, depth);
		return readNode.executeRead(parentFrame);
	}

	@Specialization(guards = "!CACHE_READ")
	protected Object readWithoutCache(VirtualFrame frame,
			@Cached("createReadNode(slot)") ReadFrameSlotNode readNode) {
		Frame parentFrame = OzArguments.getParentFrame(frame, depth);
		return readNode.executeRead(parentFrame);
	}

	protected ReadFrameSlotNode createReadNode(FrameSlot slot) {
		return ReadFrameSlotNodeGen.create(slot);
	}

	protected CachingReadFrameSlotNode createCachingReadNode(FrameSlot slot) {
		return CachingReadFrameSlotNodeGen.create(slot);
	}

}
