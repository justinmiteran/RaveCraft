package com.ravecraft.mod.core.acoustics;

/**
 * Conversions between decibels and ratios. An amplitude ratio (a gain applied to samples) takes 20 dB per decade, a
 * power or energy ratio 10 dB per decade.
 */
public final class Decibels {
	/** Decibels per decade of an amplitude ratio. */
	public static final double AMPLITUDE_DB = 20;
	/** Decibels per decade of a power ratio. */
	public static final double POWER_DB = 10;
	/** The base of the logarithms: decibels count decades. */
	public static final double DECADE = 10;

	private Decibels() {
	}

	/** The amplitude ratio of {@code db} decibels. */
	public static double toAmplitude(double db) {
		return Math.pow(DECADE, db / AMPLITUDE_DB);
	}

	/** The decibels of an amplitude ratio; negative infinity for 0. */
	public static double fromAmplitude(double ratio) {
		return AMPLITUDE_DB * Math.log10(ratio);
	}

	/** The power ratio of {@code db} decibels. */
	public static double toPower(double db) {
		return Math.pow(DECADE, db / POWER_DB);
	}

	/** The decibels of a power ratio; negative infinity for 0. */
	public static double fromPower(double ratio) {
		return POWER_DB * Math.log10(ratio);
	}
}
