package com.ravecraft.mod.core.acoustics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DirectivityTest {
	private static final Directivity CONE = new Directivity(45, -12);

	private static double cosDeg(double degrees) {
		return Math.cos(Math.toRadians(degrees));
	}

	@Test
	void partyFullGainAtSixtyDegrees() {
		assertEquals(1, DeviceProfile.PARTY.directivity().gain(cosDeg(60)), 1e-9);
	}

	@Test
	void partyMinusTenAtRear() {
		assertEquals(-10, 20 * Math.log10(DeviceProfile.PARTY.directivity().gain(-1)), 1e-9);
	}

	@Test
	void omniIsFlat() {
		assertEquals(1, Directivity.OMNI.gain(1), 1e-9);
		assertEquals(1, Directivity.OMNI.gain(0), 1e-9);
		assertEquals(1, Directivity.OMNI.gain(-1), 1e-9);
	}

	@Test
	void coneFullInsideHalfAngle() {
		assertEquals(1, CONE.gain(cosDeg(30)), 1e-9);
	}

	@Test
	void coneRearFloor() {
		assertEquals(-12, 20 * Math.log10(CONE.gain(-1)), 1e-9);
	}

	@Test
	void coneDecreasesOffAxis() {
		double rear = CONE.gain(-1);
		double g60 = CONE.gain(cosDeg(60));
		double g90 = CONE.gain(cosDeg(90));
		double g135 = CONE.gain(cosDeg(135));
		assertTrue(g60 < 1 && g60 > g90 && g90 > g135 && g135 > rear, g60 + " " + g90 + " " + g135);
	}
}
