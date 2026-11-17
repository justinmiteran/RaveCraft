package com.ravecraft.mod.core.acoustics.occlusion;

/**
 * The occlusion between a source and a listener, all paths combined: {@code lossDb} broadband (infinite when nothing
 * gets through) and {@code highLossDb} extra treble on top of it.
 */
public record Occlusion(double lossDb, double highLossDb) {
	/** Nothing in the way. */
	public static final Occlusion NONE = new Occlusion(0, 0);
	/** Nothing gets through. */
	public static final Occlusion MUTE = new Occlusion(Double.POSITIVE_INFINITY, 0);

	/** True when nothing gets through. */
	public boolean mute() {
		return Double.isInfinite(lossDb);
	}
}
