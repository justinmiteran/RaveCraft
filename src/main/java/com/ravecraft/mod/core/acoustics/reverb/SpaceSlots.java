package com.ravecraft.mod.core.acoustics.reverb;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Chooses which speakers' spaces occupy the two speaker-reverb slots (S1, S2). */
public final class SpaceSlots {
	public static final int SLOTS = 2;

	private SpaceSlots() {
	}

	/**
	 * Returns a new array of length {@link #SLOTS} holding the loudest session ids with gain above
	 * zero. An id present in {@code previous} and still selected keeps its index; the others fill the
	 * free indexes in loudness order. Ties are broken by the smaller session id.
	 */
	public static Integer[] assign(Map<Integer, Double> outsideGains, Integer[] previous) {
		List<Map.Entry<Integer, Double>> ranked = new ArrayList<>();
		for (Map.Entry<Integer, Double> e : outsideGains.entrySet()) {
			Double g = e.getValue();
			if (g != null && g > 0 && !Double.isNaN(g)) {
				ranked.add(e);
			}
		}
		ranked.sort((a, b) -> {
			int c = Double.compare(b.getValue(), a.getValue());
			return c != 0 ? c : Integer.compare(a.getKey(), b.getKey());
		});
		int n = Math.min(SLOTS, ranked.size());
		Integer[] result = new Integer[SLOTS];
		boolean[] placed = new boolean[n];
		if (previous != null) {
			for (int i = 0; i < Math.min(SLOTS, previous.length); i++) {
				Integer p = previous[i];
				if (p == null) {
					continue;
				}
				for (int j = 0; j < n; j++) {
					if (!placed[j] && ranked.get(j).getKey().equals(p)) {
						result[i] = p;
						placed[j] = true;
						break;
					}
				}
			}
		}
		int free = 0;
		for (int j = 0; j < n; j++) {
			if (placed[j]) {
				continue;
			}
			while (result[free] != null) {
				free++;
			}
			result[free] = ranked.get(j).getKey();
		}
		return result;
	}

	/** True when the occlusion is unknown or its loss is below the same-space threshold. */
	public static boolean sameSpace(boolean occlusionKnown, double occlusionLossDb, double sameSpaceLossDb) {
		return !occlusionKnown || occlusionLossDb < sameSpaceLossDb;
	}
}
