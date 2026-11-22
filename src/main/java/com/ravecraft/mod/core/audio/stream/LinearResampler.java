package com.ravecraft.mod.core.audio.stream;

import java.util.Arrays;

/**
 * Streaming mono resampler to {@link StreamFormat#SAMPLE_RATE} by linear interpolation. It keeps its position and
 * the last input sample between calls, so audio cut into blocks of any size resamples without clicks or drift.
 */
public final class LinearResampler {
	private final double step;
	private final boolean identity;
	/** Position of the next output sample, in input samples relative to the start of the next block. */
	private double position;
	private short last;
	private boolean started;

	/**
	 * Creates a resampler.
	 *
	 * @param fromRate the input sample rate, positive
	 */
	public LinearResampler(int fromRate) {
		if (fromRate <= 0) {
			throw new IllegalArgumentException("Invalid sample rate " + fromRate);
		}
		this.step = fromRate / (double) StreamFormat.SAMPLE_RATE;
		this.identity = fromRate == StreamFormat.SAMPLE_RATE;
	}

	/**
	 * Resamples the next block.
	 *
	 * @param in the input samples
	 * @param length the samples of {@code in} to read
	 * @return the output samples; their count varies from block to block
	 */
	public short[] process(short[] in, int length) {
		if (identity) {
			return Arrays.copyOf(in, length);
		}
		if (length == 0) {
			return new short[0];
		}
		if (!started) {
			// Index -1 is the previous block's last sample; before the first block, start exactly on sample 0.
			started = true;
			last = in[0];
		}
		int capacity = (int) Math.ceil((length - position) / step) + 2;
		short[] out = new short[Math.max(capacity, 0)];
		int count = 0;
		while (true) {
			int index = (int) Math.floor(position);
			if (index + 1 >= length) {
				break;
			}
			double fraction = position - index;
			double a = index < 0 ? last : in[index];
			double b = in[index + 1];
			out[count++] = (short) Math.round(a + (b - a) * fraction);
			position += step;
		}
		last = in[length - 1];
		position -= length;
		return Arrays.copyOf(out, count);
	}
}
