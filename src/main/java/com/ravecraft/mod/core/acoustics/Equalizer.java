package com.ravecraft.mod.core.acoustics;

/**
 * A low-shelf filter (RBJ biquad, Q 0.707) shaping a device's bass, applied to mono PCM before it reaches OpenAL.
 * Keeps its state between calls, so consecutive blocks join without clicks. Not thread-safe: one per stream.
 */
public final class Equalizer {
	private static final double Q = 0.707;

	private final boolean bypass;
	private final double b0;
	private final double b1;
	private final double b2;
	private final double a1;
	private final double a2;
	private double z1;
	private double z2;

	/**
	 * Creates the filter; a gain of 0 dB bypasses it.
	 *
	 * @param sampleRate the stream's sample rate in hertz
	 * @param shelfHz the shelf's corner frequency in hertz
	 * @param gainDb the bass gain in decibels, negative to cut
	 */
	public Equalizer(double sampleRate, double shelfHz, double gainDb) {
		this.bypass = gainDb == 0;
		double a = Decibels.toAmplitude(gainDb / 2);
		double w0 = 2 * Math.PI * shelfHz / sampleRate;
		double c = Math.cos(w0);
		double alpha = Math.sin(w0) / (2 * Q);
		double k = 2 * Math.sqrt(a) * alpha;
		double a0 = (a + 1) + (a - 1) * c + k;
		this.b0 = a * ((a + 1) - (a - 1) * c + k) / a0;
		this.b1 = 2 * a * ((a - 1) - (a + 1) * c) / a0;
		this.b2 = a * ((a + 1) - (a - 1) * c - k) / a0;
		this.a1 = -(2 * ((a - 1) + (a + 1) * c)) / a0;
		this.a2 = ((a + 1) + (a - 1) * c - k) / a0;
	}

	/**
	 * Filters the first {@code count} samples in place; the output stays within [−1, 1].
	 *
	 * @param samples the samples to filter
	 * @param count how many samples to filter
	 */
	public void process(float[] samples, int count) {
		if (bypass) {
			return;
		}
		for (int i = 0; i < count; i++) {
			double x = samples[i];
			double y = b0 * x + z1;
			z1 = b1 * x - a1 * y + z2;
			z2 = b2 * x - a2 * y;
			samples[i] = (float) Math.clamp(y, -1.0, 1.0);
		}
	}
}
