package com.ravecraft.mod.core.acoustics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ravecraft.mod.core.speaker.CarryPlace;
import org.junit.jupiter.api.Test;

class SpeakerAxisTest {
	private static final double DELTA = 1e-9;

	@Test
	void blockFollowsFacing() {
		SpeakerAxis axis = SpeakerAxis.block(1, 0);
		assertEquals(1, axis.x(), DELTA);
		assertEquals(0, axis.z(), DELTA);
		assertTrue(axis.directed());
	}

	@Test
	void handFollowsLook() {
		SpeakerAxis axis = SpeakerAxis.carried(CarryPlace.HAND, 90, 0);
		assertEquals(-1, axis.x(), DELTA);
		assertEquals(0, axis.z(), DELTA);
		assertTrue(axis.directed());
	}

	@Test
	void inventoryFollowsLookNotBody() {
		SpeakerAxis axis = SpeakerAxis.carried(CarryPlace.INVENTORY, 90, 180);
		assertEquals(-1, axis.x(), DELTA);
		assertEquals(0, axis.z(), DELTA);
	}

	@Test
	void backPointsBehind() {
		SpeakerAxis axis = SpeakerAxis.carried(CarryPlace.BACK, 45, 0);
		assertEquals(0, axis.x(), DELTA);
		assertEquals(-1, axis.z(), DELTA);
		assertTrue(axis.directed());
	}

	@Test
	void groundIsOmni() {
		assertEquals(Directivity.OMNI, SpeakerAxis.GROUND.directivity(DeviceProfile.PARTY));
		assertFalse(SpeakerAxis.GROUND.directed());
	}

	@Test
	void directedKeepsProfile() {
		assertEquals(DeviceProfile.PARTY.directivity(), SpeakerAxis.block(0, 1).directivity(DeviceProfile.PARTY));
	}
}
