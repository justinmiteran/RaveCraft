package com.ravecraft.mod.core.acoustics.occlusion;

/**
 * The loss along one straight segment: {@code lossDb} broadband, {@code highLossDb} extra treble on top of it, both
 * infinite when nothing gets through; {@code length} is the segment length in blocks.
 */
public record PathLoss(double lossDb, double highLossDb, double length) {
	/** A segment that transmits nothing (a vacuum on the way). */
	public static PathLoss mute(double length) {
		return new PathLoss(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, length);
	}

	/** True when nothing gets through. */
	public boolean mute() {
		return Double.isInfinite(lossDb);
	}
}
