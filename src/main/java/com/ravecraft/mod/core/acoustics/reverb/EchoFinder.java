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
	 * Distances here are wall distances ({@link RayHit#wallDistance}): every ray that met a flat wall, whatever its
	 * angle, tells the same distance, the one of the wall's mirror image. Candidates are the closed hits that absorb
	 * at most {@code maxAbsorption} and lie within [{@code minDistance}, {@code maxDistance}]. They are sorted by
	 * distance; for each candidate c, its group is every candidate within 15 % of c's distance
	 * (|d - c.d| &lt;= 0.15 * c.d). The group with the most members
	 * wins, ties going to the one whose centre c is nearest. The echo comes from that group's median distance d:
	 * {@code delay = 2 d / 343} and {@code gain = clamp(gain * (1 - mean absorption) * (size / hits) * (17 / d), 0, 1)}.
	 * Fewer than {@code minRays} members, or no candidate, gives {@link Echo#NONE}.
	 */
	public static Echo find(List<RayHit> hits, EchoSettings settings) {
		List<Integer> group = group(hits, settings);
		if (group.isEmpty()) {
			return Echo.NONE;
		}
		List<RayHit> best = new ArrayList<>(group.size());
		for (int i : group) {
			best.add(hits.get(i));
		}
		best.sort(Comparator.comparingDouble(RayHit::wallDistance));

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

	/**
	 * The indices into {@code hits}, ascending, of the group {@link #find} makes its echo from; empty when it finds
	 * {@link Echo#NONE}.
	 */
	public static List<Integer> group(List<RayHit> hits, EchoSettings settings) {
		List<Integer> candidates = new ArrayList<>();
		for (int i = 0; i < hits.size(); i++) {
			RayHit hit = hits.get(i);
			if (!hit.open() && hit.absorption() <= settings.maxAbsorption()
					&& hit.wallDistance() >= settings.minDistance() && hit.wallDistance() <= settings.maxDistance()) {
				candidates.add(i);
			}
		}
		candidates.sort(Comparator.comparingDouble(i -> hits.get(i).wallDistance()));

		List<Integer> best = List.of();
		for (int center : candidates) {
			double distance = hits.get(center).wallDistance();
			double limit = TOLERANCE * distance;
			List<Integer> group = new ArrayList<>();
			for (int other : candidates) {
				if (Math.abs(hits.get(other).wallDistance() - distance) <= limit) {
					group.add(other);
				}
			}
			// Candidates go nearest first: on a tie the nearest centre keeps the lead.
			if (group.size() > best.size()) {
				best = group;
			}
		}
		if (best.isEmpty() || best.size() < settings.minRays()) {
			return List.of();
		}
		List<Integer> sorted = new ArrayList<>(best);
		sorted.sort(null);
		return List.copyOf(sorted);
	}

	/** The median wall distance of a group already sorted by it. */
	private static double median(List<RayHit> sorted) {
		int n = sorted.size();
		return n % 2 == 1 ? sorted.get(n / 2).wallDistance()
				: (sorted.get(n / 2 - 1).wallDistance() + sorted.get(n / 2).wallDistance()) / 2;
	}
}
