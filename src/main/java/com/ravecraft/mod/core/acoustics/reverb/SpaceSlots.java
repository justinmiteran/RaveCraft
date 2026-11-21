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
		List<Integer> ranked = ranked(outsideGains);
		Integer[] result = new Integer[SLOTS];
		List<Integer> unplaced = new ArrayList<>(ranked);
		if (previous != null) {
			for (int i = 0; i < Math.min(SLOTS, previous.length); i++) {
				Integer p = previous[i];
				if (p != null && unplaced.remove(p)) {
					result[i] = p;
				}
			}
		}
		int free = 0;
		for (Integer id : unplaced) {
			while (result[free] != null) {
				free++;
			}
			result[free] = id;
		}
		return result;
	}

	/** The ids with a gain above zero, loudest first and the smaller id first on a tie, at most {@link #SLOTS}. */
	private static List<Integer> ranked(Map<Integer, Double> outsideGains) {
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
		List<Integer> ids = new ArrayList<>();
		for (int j = 0; j < Math.min(SLOTS, ranked.size()); j++) {
			ids.add(ranked.get(j).getKey());
		}
		return ids;
	}

	/** True when the occlusion is unknown or its loss is below the same-space threshold. */
	public static boolean sameSpace(boolean occlusionKnown, double occlusionLossDb, double sameSpaceLossDb) {
		return !occlusionKnown || occlusionLossDb < sameSpaceLossDb;
	}

	/**
	 * Same-space test with a 1 dB hysteresis: a speaker already in the listener's space stays there until its loss
	 * reaches {@code threshold + 1}, and joins only below {@code threshold - 1}. Unknown occlusion counts as same.
	 */
	public static boolean sameSpace(boolean occlusionKnown, double occlusionLossDb, double sameSpaceLossDb,
			boolean wasSame) {
		if (!occlusionKnown) {
			return true;
		}
		return wasSame ? occlusionLossDb < sameSpaceLossDb + 1 : occlusionLossDb < sameSpaceLossDb - 1;
	}
}
