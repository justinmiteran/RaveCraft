package com.ravecraft.mod.core.audio.stream;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/** Cuts a stream of mono samples into frames of {@link StreamFormat#FRAME_SAMPLES} samples. */
public final class MonoFramer {
	private final Deque<short[]> ready = new ArrayDeque<>();
	private short[] partial = new short[StreamFormat.FRAME_SAMPLES];
	private int filled;

	/**
	 * Adds the first {@code length} samples; each frame they complete becomes ready for {@link #poll()}.
	 *
	 * @param samples the samples to add
	 * @param length how many samples to add
	 */
	public void push(short[] samples, int length) {
		int at = 0;
		while (at < length) {
			int count = Math.min(length - at, partial.length - filled);
			System.arraycopy(samples, at, partial, filled, count);
			filled += count;
			at += count;
			if (filled == partial.length) {
				ready.add(partial);
				partial = new short[StreamFormat.FRAME_SAMPLES];
				filled = 0;
			}
		}
	}

	/**
	 * The next complete frame, or null when none is ready.
	 *
	 * @return the frame, or null
	 */
	public short[] poll() {
		return ready.poll();
	}

	/**
	 * The remaining samples padded with silence to a full frame, or null when nothing is left.
	 *
	 * @return the last frame, or null
	 */
	public short[] flush() {
		if (filled == 0) {
			return null;
		}
		short[] frame = Arrays.copyOf(partial, partial.length);
		Arrays.fill(frame, filled, frame.length, (short) 0);
		filled = 0;
		return frame;
	}
}
