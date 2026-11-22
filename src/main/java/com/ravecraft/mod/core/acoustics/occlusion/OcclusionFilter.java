package com.ravecraft.mod.core.acoustics.occlusion;

import com.ravecraft.mod.core.acoustics.Decibels;

/**
 * The treble side of occlusion: a first-order low-pass (bilinear, 6 dB per octave, like the mass law) applied to a
 * stream's mono PCM. Its cutoff is set so the filter loses exactly {@code highLossDb} at {@link #REFERENCE_HZ}; with
 * no loss the signal passes untouched. A new loss is reached by gliding over the block, so a door closing does not
 * click. Keeps its state between calls. Not thread-safe: one per stream.
 */
public final class OcclusionFilter {
	/** The frequency at which {@code highLossDb} is measured. */
	public static final double REFERENCE_HZ = 4000;
	/** The lowest cutoff, which caps the treble loss (about 46 dB at the reference frequency). */
	public static final double MIN_CUTOFF_HZ = 20;
	/** Samples per coefficient update while gliding (1 ms at 48 kHz). */
	private static final int CHUNK = 48;

	private final double referenceTan;
	private final double minK;
	private double lossDb;
	private double x1;
	private double y1;

	/**
	 * Creates a filter with no loss.
	 *
	 * @param sampleRate the stream's sample rate in hertz
	 */
	public OcclusionFilter(double sampleRate) {
		this.referenceTan = Math.tan(Math.PI * REFERENCE_HZ / sampleRate);
		this.minK = Math.tan(Math.PI * MIN_CUTOFF_HZ / sampleRate);
	}

	/**
	 * Filters the first {@code count} samples in place, gliding from the previous loss to {@code highLossDb}.
	 *
	 * @param samples the samples to filter
	 * @param count how many samples to filter
	 * @param highLossDb the treble loss to reach, in decibels
	 */
	public void process(float[] samples, int count, double highLossDb) {
		double from = lossDb;
		double to = Math.max(0, highLossDb);
		for (int start = 0; start < count; start += CHUNK) {
			int end = Math.min(count, start + CHUNK);
			double loss = from + (to - from) * end / count;
			if (loss <= 0) {
				// Untouched, but the state follows the signal so the filter can start from it without a jump.
				for (int i = start; i < end; i++) {
					x1 = samples[i];
					y1 = samples[i];
				}
				continue;
			}
			// |H| at the reference is K / sqrt(K² + tan²), so this K loses exactly `loss` there.
			double k = Math.max(minK, referenceTan / Math.sqrt(Decibels.toPower(loss) - 1));
			double b = k / (1 + k);
			double a = (k - 1) / (k + 1);
			for (int i = start; i < end; i++) {
				double x = samples[i];
				double y = b * (x + x1) - a * y1;
				x1 = x;
				y1 = y;
				samples[i] = (float) Math.clamp(y, -1.0, 1.0);
			}
		}
		lossDb = to;
	}
}
