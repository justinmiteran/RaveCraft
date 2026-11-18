package com.ravecraft.mod.core.acoustics.occlusion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ListenerPerceptionTest {
	@Test
	void noMediumChangesNothing() {
		assertSame(ListenerPerception.NONE, ListenerPerception.of(null));
		assertEquals(new ListenerPerception(0, 0, 0), ListenerPerception.NONE);
	}

	@Test
	void takesTheMediumsPerception() {
		AcousticMedium water = OcclusionSettings.DEFAULT.medium("water");
		assertEquals(new ListenerPerception(10, 12, 0.8), ListenerPerception.of(water));
	}

	@Test
	void airChangesNothing() {
		assertEquals(ListenerPerception.NONE, ListenerPerception.of(OcclusionSettings.DEFAULT.medium("air")));
	}

	@Test
	void headIsInTheFluidOnlyBelowItsSurface() {
		// A source block of water fills 8/9 of its block.
		assertTrue(ListenerPerception.headInFluid(64.5, 64, 8 / 9.0));
		assertFalse(ListenerPerception.headInFluid(64.95, 64, 8 / 9.0));
		assertFalse(ListenerPerception.headInFluid(64.5, 64, 0));
	}
}
