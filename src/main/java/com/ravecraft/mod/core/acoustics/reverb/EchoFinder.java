package com.ravecraft.mod.core.acoustics.reverb;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Finds the distinct echo, if any, in the hits of a room probe. */
public final class EchoFinder {
	private static final double SPEED_OF_SOUND = 343;
	private static final double REFERENCE_DISTANCE = 17;
	private static final double TOLERANCE = 0.15;

	private EchoFinder() {
	}

	/**
	 * Candidates are the closed hits that absorb at most {@code maxAbsorption} and lie within
	 * [{@code minDistance}, {@code maxDistance}]. They are sorted by distance; for each candidate c, its group is
	 * every candidate within 15 % of c's distance (|d - c.d| &lt;= 0.15 * c.d). The group with the most members
	 * wins, ties going to the one whose centre c is nearest. The echo comes from that group's median distance d:
	 * {@code delay = 2 d / 343} and {@code gain = clamp(gain * (1 - mean absorption) * (size / hits) * (17 / d), 0, 1)}.
	 * Fewer than {@code minRays} members, or no candidate, gives {@link Echo#NONE}.
	 */
	public static Echo find(List<RayHit> hits, EchoSettings settings) {
		List<RayHit> candidates = new ArrayList<>();
		for (RayHit hit : hits) {
			if (!hit.open() && hit.absorption() <= settings.maxAbsorption() && hit.distance() >= settings.minDistance()
					&& hit.distance() <= settings.maxDistance()) {
				candidates.add(hit);
			}
		}
		candidates.sort(Comparator.comparingDouble(RayHit::distance));

		List<RayHit> best = List.of();
		for (RayHit center : candidates) {
			double limit = TOLERANCE * center.distance();
			List<RayHit> group = new ArrayList<>();
			for (RayHit other : candidates) {
				if (Math.abs(other.distance() - center.distance()) <= limit) {
					group.add(other);
				}
			}
			if (group.size() > best.size()) {
				best = group;
			}
		}
		if (best.isEmpty() || best.size() < settings.minRays()) {
			return Echo.NONE;
		}

		double d = median(best);
		double absorption = 0;
		for (RayHit hit : best) {
			absorption += hit.absorption();
		}
		absorption /= best.size();
		double gain = settings.gain() * (1 - absorption) * ((double) best.size() / hits.size())
				* (REFERENCE_DISTANCE / d);
		return new Echo(2 * d / SPEED_OF_SOUND, Math.clamp(gain, 0, 1));
	}

	/** The median distance of a group already sorted by distance. */
	private static double median(List<RayHit> sorted) {
		int n = sorted.size();
		return n % 2 == 1 ? sorted.get(n / 2).distance()
				: (sorted.get(n / 2 - 1).distance() + sorted.get(n / 2).distance()) / 2;
	}
}
