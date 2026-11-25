package com.ravecraft.mod.core.acoustics;

/**
 * A low-shelf filter ({@link Shelf}) shaping a device's bass, applied to mono PCM before it reaches OpenAL.
 * Keeps its state between calls, so consecutive blocks join without clicks. Not thread-safe: one per stream.
 */
public final class Equalizer {
	private final Shelf shelf;

	/**
	 * Creates the filter; a gain of 0 dB leaves the signal untouched.
	 *
	 * @param sampleRate the stream's sample rate in hertz
	 * @param shelfHz the shelf's corner frequency in hertz
	 * @param gainDb the bass gain in decibels, negative to cut
	 */
	public Equalizer(double sampleRate, double shelfHz, double gainDb) {
		shelf = Shelf.low(sampleRate, shelfHz);
		shelf.gainDb(gainDb);
	}

	/**
	 * Filters the first {@code count} samples in place; the output stays within [−1, 1].
	 *
	 * @param samples the samples to filter
	 * @param count how many samples to filter
	 */
	public void process(float[] samples, int count) {
		for (int i = 0; i < count; i++) {
			samples[i] = (float) Math.clamp(shelf.next(samples[i]), -1.0, 1.0);
		}
	}
}
