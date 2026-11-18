package com.ravecraft.mod.core.acoustics.reverb;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SpaceSlotsTest {
	@Test
	void picksTheTwoLoudest() {
		Map<Integer, Double> g = Map.of(1, 0.2, 2, 0.9, 3, 0.5);
		assertArrayEquals(new Integer[] {2, 3}, SpaceSlots.assign(g, null));
	}

	@Test
	void keepsAnIdInItsSlot() {
		Map<Integer, Double> g = Map.of(9, 0.5, 3, 0.45, 7, 0.4);
		assertArrayEquals(new Integer[] {3, 9}, SpaceSlots.assign(g, new Integer[] {7, 9}));
	}

	@Test
	void nearlyEqualGainsDoNotSwapSlots() {
		Map<Integer, Double> g = Map.of(1, 0.300, 2, 0.301);
		assertArrayEquals(new Integer[] {1, 2}, SpaceSlots.assign(g, new Integer[] {1, 2}));
	}

	@Test
	void silentSpeakersGetNoSlot() {
		Map<Integer, Double> g = new HashMap<>();
		g.put(1, 0.0);
		g.put(2, Double.NaN);
		g.put(3, -1.0);
		g.put(4, 0.2);
		assertArrayEquals(new Integer[] {4, null}, SpaceSlots.assign(g, null));
		assertArrayEquals(new Integer[] {null, null}, SpaceSlots.assign(Map.of(), new Integer[] {5}));
	}

	@Test
	void tiesGoToTheSmallerId() {
		Map<Integer, Double> g = Map.of(8, 0.5, 4, 0.5, 6, 0.5);
		assertArrayEquals(new Integer[] {4, 6}, SpaceSlots.assign(g, null));
	}

	@Test
	void sameSpaceBelowThresholdOrUnknown() {
		assertTrue(SpaceSlots.sameSpace(false, 99, 6));
		assertTrue(SpaceSlots.sameSpace(true, 3, 6));
		assertFalse(SpaceSlots.sameSpace(true, 6, 6));
		assertFalse(SpaceSlots.sameSpace(true, 12, 6));
	}

	@Test
	void hysteresisKeepsSameUntilThresholdPlusOne() {
		assertTrue(SpaceSlots.sameSpace(true, 6.5, 6, true));
		assertFalse(SpaceSlots.sameSpace(true, 7.0, 6, true));
		assertTrue(SpaceSlots.sameSpace(false, 99, 6, false));
	}

	@Test
	void hysteresisJoinsOnlyBelowThresholdMinusOne() {
		assertFalse(SpaceSlots.sameSpace(true, 5.5, 6, false));
		assertFalse(SpaceSlots.sameSpace(true, 5.0, 6, false));
		assertTrue(SpaceSlots.sameSpace(true, 4.9, 6, false));
	}
}
