package com.ravecraft.mod.core.acoustics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SourceSelectorTest {
	@Test
	void keepsLoudest() {
		assertEquals(Set.of(2, 3), SourceSelector.select(Map.of(1, 0.1, 2, 0.5, 3, 0.3), Set.of(), 2));
	}

	@Test
	void tieBrokenByLowestId() {
		assertEquals(Set.of(4), SourceSelector.select(Map.of(5, 0.2, 4, 0.2), Set.of(), 1));
	}

	@Test
	void activeStreamKeepsSourceWithinOneDecibel() {
		assertEquals(Set.of(1), SourceSelector.select(Map.of(1, 0.50, 2, 0.55), Set.of(1), 1));
	}

	@Test
	void louderByMoreThanOneDecibelTakesOver() {
		assertEquals(Set.of(2), SourceSelector.select(Map.of(1, 0.50, 2, 0.60), Set.of(1), 1));
	}

	@Test
	void selectorShrinksWhenMaxDrops() {
		Map<Integer, Double> gains = Map.of(1, 0.1, 2, 0.2, 3, 0.3, 4, 0.4, 5, 0.5);
		assertEquals(Set.of(4, 5), SourceSelector.select(gains, Set.of(1, 2, 3, 4, 5), 2));
	}

	@Test
	void selectorNeverGivesSourceToSilentStream() {
		assertEquals(Set.of(2), SourceSelector.select(Map.of(1, 0.0, 2, 0.3), Set.of(), 8));
	}
}
