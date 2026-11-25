package com.ravecraft.mod.core.acoustics.radiation;

import com.ravecraft.mod.core.acoustics.Decibels;

/**
 * How a speaker radiates, in the few numbers its tooltip shows.
 *
 * @param omni whether it is heard alike all around
 * @param coverageDeg the angle in front of it where the highs stay clear, in degrees
 * @param rearLossDb how much quieter the mids are straight behind, in decibels
 */
public record RadiationSummary(boolean omni, int coverageDeg, int rearLossDb) {
	private static final int FULL_TURN_DEG = 360;
	private static final int BEHIND_DEG = 180;
	private static final double OMNI_LOSS_DB = 3;
	private static final double COVERAGE_LOSS_DB = 6;
	private static final int COVERAGE_STEP_DEG = 10;

	/**
	 * The summary of a pattern.
	 *
	 * @param pattern the pattern
	 * @return its summary
	 */
	public static RadiationSummary of(RadiationPattern pattern) {
		boolean omni = true;
		int covered = 0;
		for (int azimuth = 0; azimuth < FULL_TURN_DEG; azimuth++) {
			BandGains gains = pattern.at(azimuth, 0);
			omni &= Decibels.fromAmplitude(gains.mid()) >= -OMNI_LOSS_DB;
			if (Decibels.fromAmplitude(gains.high()) > -COVERAGE_LOSS_DB) {
				covered++;
			}
		}
		if (omni) {
			return new RadiationSummary(true, FULL_TURN_DEG, 0);
		}
		int coverage = (int) Math.round((double) covered / COVERAGE_STEP_DEG) * COVERAGE_STEP_DEG;
		int rear = (int) Math.round(-Decibels.fromAmplitude(pattern.at(BEHIND_DEG, 0).mid()));
		return new RadiationSummary(false, coverage, rear);
	}
}
