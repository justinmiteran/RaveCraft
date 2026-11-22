package com.ravecraft.mod.core.acoustics.occlusion;

import com.ravecraft.mod.core.acoustics.Decibels;

/** The gain a stream plays at once its occlusion is applied. */
public final class OcclusionGate {
	private OcclusionGate() {
	}

	/**
	 * {@code gain} (propagation) after {@code lossDb} of occlusion when it is {@code known}, else 0: a stream not traced
	 * yet is held silent rather than heard unmuffled through walls (the scheduler traces it within a tick). A stream
	 * out of range or at volume 0 has a gain of 0 either way.
	 *
	 * @param gain the propagation gain
	 * @param known true once the stream has been traced
	 * @param lossDb the occlusion loss, in decibels
	 * @return the gain after occlusion
	 */
	public static double gain(double gain, boolean known, double lossDb) {
		return known ? gain * Decibels.toAmplitude(-lossDb) : 0;
	}
}
