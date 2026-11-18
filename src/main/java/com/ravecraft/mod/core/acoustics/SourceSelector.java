package com.ravecraft.mod.core.acoustics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Chooses which streams get one of the limited OpenAL sources: the loudest at the listener. A stream that already
 * has a source keeps it unless another one is louder by more than 1 dB, so two speakers at similar levels do not
 * swap back and forth.
 */
public final class SourceSelector {
	/** +1 dB in linear gain. */
	public static final double HYSTERESIS = Math.pow(10, 1 / 20.0);

	private SourceSelector() {
	}

	/** The session ids that get a source, out of {@code gains} (linear, before player volumes). */
	public static Set<Integer> select(Map<Integer, Double> gains, Set<Integer> active, int max) {
		List<Map.Entry<Integer, Double>> scored = new ArrayList<>();
		for (Map.Entry<Integer, Double> entry : gains.entrySet()) {
			double gain = entry.getValue();
			if (gain > 0) {
				double score = active.contains(entry.getKey()) ? gain * HYSTERESIS : gain;
				scored.add(Map.entry(entry.getKey(), score));
			}
		}
		scored.sort(Comparator.<Map.Entry<Integer, Double>>comparingDouble(Map.Entry::getValue).reversed()
				.thenComparing(Map.Entry::getKey));
		Set<Integer> chosen = new HashSet<>();
		for (int i = 0; i < Math.min(max, scored.size()); i++) {
			chosen.add(scored.get(i).getKey());
		}
		return chosen;
	}
}
