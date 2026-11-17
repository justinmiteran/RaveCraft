package com.ravecraft.mod.core.audio.stream;

import java.util.List;
import java.util.TreeMap;

/**
 * Reorders the frames of one stream and hands them out at playout pace. It buffers until {@code targetFrames} are
 * spanned before playing, reports missing frames as {@link Lost}, and after an underrun or a jump far ahead it starts
 * again from the newest frames instead of concealing the whole gap. Not thread-safe.
 */
public final class JitterBuffer {
	public sealed interface Slot permits Audio, Lost, Waiting {
	}

	/** The frame due now. */
	public record Audio(byte[] frame) implements Slot {
	}

	/** The frame due now never arrived, but later ones did. */
	public record Lost() implements Slot {
	}

	/** Not playing yet: still buffering. */
	public record Waiting() implements Slot {
	}

	private static final Lost LOST = new Lost();
	private static final Waiting WAITING = new Waiting();

	private final int targetFrames;
	private final int maxAheadFrames;
	private final int capacity;
	private final TreeMap<Long, byte[]> frames = new TreeMap<>();
	/** Index of the next frame to play; frames below it are too old. */
	private long cursor = Long.MIN_VALUE;
	private boolean playing;

	public JitterBuffer(int targetFrames, int maxAheadFrames) {
		this.targetFrames = targetFrames;
		this.maxAheadFrames = maxAheadFrames;
		this.capacity = maxAheadFrames + targetFrames;
	}

	/** Adds consecutive frames, the first having index {@code firstFrame}. */
	public void offer(long firstFrame, List<byte[]> chunk) {
		for (int i = 0; i < chunk.size(); i++) {
			long index = firstFrame + i;
			if (index < cursor || frames.containsKey(index)) {
				continue;
			}
			if (playing && index > cursor + maxAheadFrames) {
				frames.clear();
				playing = false;
			}
			frames.put(index, chunk.get(i));
		}
		while (frames.size() > capacity) {
			frames.pollFirstEntry();
		}
	}

	public Slot poll() {
		if (!playing) {
			if (frames.isEmpty() || frames.lastKey() - frames.firstKey() < targetFrames) {
				return WAITING;
			}
			playing = true;
			cursor = frames.firstKey();
		}
		if (frames.isEmpty()) {
			playing = false;
			return WAITING;
		}
		byte[] frame = frames.remove(cursor);
		cursor++;
		return frame == null ? LOST : new Audio(frame);
	}

	public int bufferedFrames() {
		return frames.size();
	}
}
