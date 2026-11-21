package com.ravecraft.mod.core.audio.stream;

import java.util.List;
import java.util.TreeMap;

/**
 * Reorders the frames of one stream and hands them out at playout pace. It buffers until {@code targetFrames} are
 * spanned before playing, reports missing frames as {@link Lost}, and after an underrun or a jump far ahead it starts
 * again from the newest frames instead of concealing the whole gap. Not thread-safe.
 */
public final class JitterBuffer {
	/** What {@link #poll()} hands out for the frame due now. */
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
	private boolean draining;
	/** Playback ran dry: the next start skips the backlog that piled up meanwhile. */
	private boolean underran;
	/** Frames never received when their turn came, and the times the buffer ran empty while playing. */
	private long lostFrames;
	private long underruns;

	/**
	 * Creates an empty buffer.
	 *
	 * @param targetFrames the frames spanned before playing starts
	 * @param maxAheadFrames how far past the cursor a frame may land before the buffer restarts from it
	 */
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

	/**
	 * Hands out the frame due now and moves to the next one.
	 *
	 * @return the frame, a lost frame, or waiting while buffering
	 */
	public Slot poll() {
		if (!playing && !start()) {
			return WAITING;
		}
		if (frames.isEmpty()) {
			playing = false;
			underran = true;
			underruns++;
			return WAITING;
		}
		byte[] frame = frames.remove(cursor);
		cursor++;
		if (frame == null) {
			lostFrames++;
			return LOST;
		}
		return new Audio(frame);
	}

	/** Starts playing once enough frames are buffered; false while still buffering. */
	private boolean start() {
		if (frames.isEmpty() || !draining && frames.lastKey() - frames.firstKey() < targetFrames) {
			return false;
		}
		playing = true;
		cursor = frames.firstKey();
		if (underran) {
			// After a stall the whole backlog arrives at once (TCP loses nothing): resume at target latency.
			cursor = Math.max(cursor, frames.lastKey() - targetFrames);
			frames.headMap(cursor).clear();
			underran = false;
		}
		return true;
	}

	/** No more frames will come: play what is buffered without waiting for the target. */
	public void drain() {
		draining = true;
	}

	/** Frames held, played or not yet. */
	public int bufferedFrames() {
		return frames.size();
	}

	/** Frames that were missing when their turn came, since the start. */
	public long lostFrames() {
		return lostFrames;
	}

	/** Times the buffer ran empty while playing (each stall counts once), since the start. */
	public long underruns() {
		return underruns;
	}
}
