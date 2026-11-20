package com.ravecraft.mod.core.audio.tags;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HoverDwellTest {
	@Test
	void loadWaitsForDwell() {
		HoverDwell dwell = new HoverDwell();
		assertFalse(dwell.ready("a", 0));
		assertFalse(dwell.ready("a", 299));
		assertTrue(dwell.ready("a", 300));
		assertTrue(dwell.ready("a", 316));
		assertFalse(dwell.ready("b", 301 + 16));
	}

	@Test
	void switchingPathRestartsTheWait() {
		HoverDwell dwell = new HoverDwell();
		assertFalse(dwell.ready("a", 0));
		assertFalse(dwell.ready("b", 200));
		assertFalse(dwell.ready("b", 499));
		assertTrue(dwell.ready("b", 500));
	}

	@Test
	void aGapInRequestsRestartsTheWait() {
		HoverDwell dwell = new HoverDwell();
		assertFalse(dwell.ready("a", 0));
		assertFalse(dwell.ready("a", 100));
		// the tooltip was not asked for during 1 s: the mouse left and came back
		assertFalse(dwell.ready("a", 1100));
		assertTrue(dwell.ready("a", 1400));
	}
}
