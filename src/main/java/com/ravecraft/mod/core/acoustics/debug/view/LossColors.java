package com.ravecraft.mod.core.acoustics.debug.view;

/**
 * The colours of the sound view, as opaque ARGB: green for no loss, yellow at 10 dB, red from 30 dB (and for a mute
 * path), linear per channel in between.
 */
public final class LossColors {
	/** An opening a detour goes through. */
	public static final int OPENING = 0xFF85B7EB;
	/** A probe ray that met nothing. */
	public static final int OPEN_RAY = 0xFF888780;
	/** A probe ray of the echo group. */
	public static final int ECHO = 0xFFFAC775;

	private static final int NO_LOSS = 0xFF5DCAA5;
	private static final int MID_LOSS = 0xFFEF9F27;
	private static final int HIGH_LOSS = 0xFFE24B4A;
	private static final double MID_DB = 10;
	private static final double HIGH_DB = 30;

	private LossColors() {
	}

	/** The colour of a loss of {@code lossDb}. */
	public static int forLoss(double lossDb) {
		if (Double.isNaN(lossDb) || lossDb >= HIGH_DB) {
			return HIGH_LOSS;
		}
		if (lossDb <= 0) {
			return NO_LOSS;
		}
		if (lossDb <= MID_DB) {
			return lerp(NO_LOSS, MID_LOSS, lossDb / MID_DB);
		}
		return lerp(MID_LOSS, HIGH_LOSS, (lossDb - MID_DB) / (HIGH_DB - MID_DB));
	}

	/** The colour of a surface absorbing {@code absorption} (0 reflects everything, 1 absorbs everything). */
	public static int forAbsorption(double absorption) {
		return forLoss(Math.clamp(absorption, 0, 1) * HIGH_DB);
	}

	/** {@code argb} with its alpha replaced by {@code alpha} (0 to 1). */
	public static int withAlpha(int argb, float alpha) {
		int a = Math.round(Math.clamp(alpha, 0f, 1f) * 255);
		return (a << 24) | (argb & 0xFFFFFF);
	}

	private static int lerp(int from, int to, double t) {
		int out = 0xFF000000;
		for (int shift = 0; shift <= 16; shift += 8) {
			int a = (from >> shift) & 0xFF;
			int b = (to >> shift) & 0xFF;
			out |= (int) Math.round(a + (b - a) * t) << shift;
		}
		return out;
	}
}
