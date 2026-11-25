package com.ravecraft.mod.core.acoustics;

/**
 * A shelf filter (RBJ biquad, Q 0.707): raises or cuts everything below (low) or above (high) its corner. Its gain may
 * change between two samples; at 0 dB the signal passes untouched. Keeps its state between calls. Not thread-safe:
 * one per stream.
 */
public final class Shelf {
	private static final double Q = 0.707;

	/** 1 for a low shelf, −1 for a high one, which mirrors the low one. */
	private final double side;
	private final double cos;
	private final double alpha;
	private double gainDb = Double.NaN;
	private double b0;
	private double b1;
	private double b2;
	private double a1;
	private double a2;
	private double z1;
	private double z2;

	private Shelf(double sampleRate, double cornerHz, double side) {
		double w0 = 2 * Math.PI * cornerHz / sampleRate;
		this.side = side;
		this.cos = side * Math.cos(w0);
		this.alpha = Math.sin(w0) / (2 * Q);
		gainDb(0);
	}

	/**
	 * A shelf on the bass, at 0 dB.
	 *
	 * @param sampleRate the stream's sample rate, in hertz
	 * @param cornerHz the corner frequency, in hertz
	 * @return the filter
	 */
	public static Shelf low(double sampleRate, double cornerHz) {
		return new Shelf(sampleRate, cornerHz, 1);
	}

	/**
	 * A shelf on the treble, at 0 dB.
	 *
	 * @param sampleRate the stream's sample rate, in hertz
	 * @param cornerHz the corner frequency, in hertz
	 * @return the filter
	 */
	public static Shelf high(double sampleRate, double cornerHz) {
		return new Shelf(sampleRate, cornerHz, -1);
	}

	/**
	 * Sets the gain of the shelf.
	 *
	 * @param db the gain, in decibels, negative to cut
	 */
	public void gainDb(double db) {
		if (db == gainDb) {
			return;
		}
		gainDb = db;
		double a = Decibels.toAmplitude(db / 2);
		double k = 2 * Math.sqrt(a) * alpha;
		double a0 = (a + 1) + (a - 1) * cos + k;
		b0 = a * ((a + 1) - (a - 1) * cos + k) / a0;
		b1 = side * 2 * a * ((a - 1) - (a + 1) * cos) / a0;
		b2 = a * ((a + 1) - (a - 1) * cos - k) / a0;
		a1 = side * -2 * ((a - 1) + (a + 1) * cos) / a0;
		a2 = ((a + 1) + (a - 1) * cos - k) / a0;
	}

	/**
	 * Filters the next sample.
	 *
	 * @param sample the sample
	 * @return the filtered sample
	 */
	public double next(double sample) {
		double y = b0 * sample + z1;
		z1 = b1 * sample - a1 * y + z2;
		z2 = b2 * sample - a2 * y;
		return y;
	}
}
