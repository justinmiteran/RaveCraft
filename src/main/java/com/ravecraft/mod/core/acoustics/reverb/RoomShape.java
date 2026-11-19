package com.ravecraft.mod.core.acoustics.reverb;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What the rays say about a space: {@code openness} (share of rays that escaped), {@code meanFreePath} (mean distance
 * to the surfaces met), {@code absorption} (mean over all rays, escapes counting 1) and {@code hitAbsorption} (mean
 * over the rays that met a surface) and {@code irregularity} (0 for flat walls, 1 for a rugged space that scatters the
 * sound; 0.5 when too few surfaces were met to tell).
 */
public record RoomShape(double openness, double meanFreePath, double absorption, double hitAbsorption,
		double irregularity) {
	private static final double MIN_RT60 = 0.1;
	/** The faces of a closed box: the number of distinct faces the smoothest enclosure shows. */
	private static final int BOX_FACES = 6;
	/** Faces beyond the six of a box at which a space counts as fully rugged. */
	private static final int RUGGED_EXTRA_FACES = 18;
	/** Irregularity when too few surfaces were met to tell. */
	private static final double UNKNOWN_IRREGULARITY = 0.5;
	private static final double MAX_ABSORPTION = 0.999;

	public static RoomShape of(List<RayHit> hits, double rayLength) {
		if (hits.isEmpty()) {
			return new RoomShape(1, rayLength, 1, 1, UNKNOWN_IRREGULARITY);
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
				all / hits.size(), closed == 0 ? 1 : hitAbsorption / closed, irregularity(hits));
	}

	/**
	 * How scattered the met faces are: rays landing on a few large flat faces (a box shows six) give 0. Each distinct
	 * face beyond those six adds to it, up to 1 at {@value #RUGGED_EXTRA_FACES} more, or as soon as every ray beyond
	 * six met a face of its own (few rays).
	 */
	static double irregularity(List<RayHit> hits) {
		Set<Long> faces = new HashSet<>();
		int faced = 0;
		for (RayHit hit : hits) {
			if (!hit.open() && hit.faceAxis() != RayHit.NO_FACE) {
				faced++;
				faces.add(((long) hit.faceAxis() << 32) | (hit.facePlane() & 0xFFFF_FFFFL));
			}
		}
		if (faced <= BOX_FACES) {
			return UNKNOWN_IRREGULARITY;
		}
		int scale = Math.min(faced - BOX_FACES, RUGGED_EXTRA_FACES);
		return Math.clamp((double) (faces.size() - BOX_FACES) / scale, 0, 1);
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
