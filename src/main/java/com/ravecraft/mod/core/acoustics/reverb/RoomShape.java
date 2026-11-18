package com.ravecraft.mod.core.acoustics.reverb;

import java.util.List;

/**
 * What the rays say about a space: {@code openness} (share of rays that escaped), {@code meanFreePath} (mean distance
 * to the surfaces met), {@code absorption} (mean over all rays, escapes counting 1) and {@code hitAbsorption} (mean
 * over the rays that met a surface).
 */
public record RoomShape(double openness, double meanFreePath, double absorption, double hitAbsorption) {
	private static final double MIN_RT60 = 0.1;
	private static final double MAX_ABSORPTION = 0.999;

	public static RoomShape of(List<RayHit> hits, double rayLength) {
		if (hits.isEmpty()) {
			return new RoomShape(1, rayLength, 1, 1);
		}
		int open = 0;
		int closed = 0;
		double distance = 0;
		double all = 0;
		double hitAbsorption = 0;
		for (RayHit hit : hits) {
			all += hit.absorption();
			if (hit.open()) {
				open++;
			} else {
				closed++;
				distance += hit.distance();
				hitAbsorption += hit.absorption();
			}
		}
		return new RoomShape((double) open / hits.size(), closed == 0 ? rayLength : distance / closed,
				all / hits.size(), closed == 0 ? 1 : hitAbsorption / closed);
	}

	/** The time the sound takes to fall 60 dB (Eyring), between 0.1 s and {@code maxDecaySeconds}. */
	public double rt60(double maxDecaySeconds) {
		double a = Math.min(absorption, MAX_ABSORPTION);
		if (a <= 0) {
			return maxDecaySeconds;
		}
		double seconds = 0.161 * meanFreePath / (-4 * Math.log(1 - a));
		return Math.clamp(seconds, MIN_RT60, Math.max(MIN_RT60, maxDecaySeconds));
	}
}
