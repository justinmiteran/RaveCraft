package com.ravecraft.mod.core.acoustics.radiation;

import com.ravecraft.mod.core.acoustics.Decibels;
import com.ravecraft.mod.core.acoustics.Shelf;

/**
 * Gives a stream's mono PCM the bass, mids and highs heard from the listener's direction: the gain of the mids, then a
 * shelf that brings the bass to its own and another for the highs. New gains are reached by gliding over the block, so
 * walking around a speaker does not click; the first block takes its gains at once. Flat gains leave the signal
 * untouched. Keeps its state between calls. Not thread-safe: one per stream.
 */
public final class RadiationFilter {
	/** Between the bass and the mids: the middle of the gap left by their bands. */
	private static final double LOW_CORNER_HZ = 315;
	/** Between the mids and the highs. */
	private static final double HIGH_CORNER_HZ = 2800;
	/** Samples per coefficient update while gliding (1 ms at 48 kHz). */
	private static final int CHUNK = 48;

	private final Shelf low;
	private final Shelf high;
	private boolean started;
	private double lowDb;
	private double midDb;
	private double highDb;

	/**
	 * Creates a filter; its first block decides where it starts.
	 *
	 * @param sampleRate the stream's sample rate, in hertz
	 */
	public RadiationFilter(double sampleRate) {
		low = Shelf.low(sampleRate, LOW_CORNER_HZ);
		high = Shelf.high(sampleRate, HIGH_CORNER_HZ);
	}

	/**
	 * Filters the first {@code count} samples in place, gliding to {@code gains}; the output stays within [−1, 1].
	 *
	 * @param samples the samples to filter
	 * @param count how many samples to filter
	 * @param gains the gains to reach, none above 1 ({@link BandGains#relative()})
	 */
	public void process(float[] samples, int count, BandGains gains) {
		double toLow = Decibels.fromAmplitude(gains.low());
		double toMid = Decibels.fromAmplitude(gains.mid());
		double toHigh = Decibels.fromAmplitude(gains.high());
		double fromLow = started ? lowDb : toLow;
		double fromMid = started ? midDb : toMid;
		double fromHigh = started ? highDb : toHigh;
		for (int start = 0; start < count; start += CHUNK) {
			int end = Math.min(count, start + CHUNK);
			double t = (double) end / count;
			tune(glide(fromLow, toLow, t), glide(fromMid, toMid, t), glide(fromHigh, toHigh, t));
			filter(samples, start, end);
		}
		tune(toLow, toMid, toHigh);
		started = true;
	}

	private static double glide(double from, double to, double t) {
		return from + (to - from) * t;
	}

	private void tune(double newLowDb, double newMidDb, double newHighDb) {
		lowDb = newLowDb;
		midDb = newMidDb;
		highDb = newHighDb;
		low.gainDb(lowDb - midDb);
		high.gainDb(highDb - midDb);
	}

	private void filter(float[] samples, int start, int end) {
		double mid = Decibels.toAmplitude(midDb);
		for (int i = start; i < end; i++) {
			samples[i] = (float) Math.clamp(high.next(low.next(samples[i] * mid)), -1.0, 1.0);
		}
	}
}
