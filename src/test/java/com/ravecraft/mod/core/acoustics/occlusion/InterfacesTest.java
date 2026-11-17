package com.ravecraft.mod.core.acoustics.occlusion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class InterfacesTest {
	private static final AcousticMedium AIR = new AcousticMedium("air", 415, 0, 0.05);
	private static final AcousticMedium WATER = new AcousticMedium("water", 1_480_000, 0, 0.02);
	private static final AcousticMedium LAVA = new AcousticMedium("lava", 6_000_000, 3, 6);
	private static final AcousticMedium VACUUM = new AcousticMedium("vacuum", 0, 0, 0);

	@Test
	void airToWaterIsAboutTwentyDb() {
		double loss = Interfaces.lossDb(AIR, WATER, 0.67);
		assertEquals(19.8, loss, 0.3);
		assertEquals(9.9, Interfaces.highLossDb(loss, 0.5), 0.2);
	}

	@Test
	void realScaleIsPhysics() {
		assertEquals(29.5, Interfaces.lossDb(AIR, WATER, 1), 0.2);
	}

	@Test
	void airToLavaIsAboutTwentyFourDb() {
		assertEquals(23.8, Interfaces.lossDb(AIR, LAVA, 0.67), 0.3);
	}

	@Test
	void symmetric() {
		assertEquals(Interfaces.lossDb(AIR, WATER, 0.67), Interfaces.lossDb(WATER, AIR, 0.67));
	}

	@Test
	void sameImpedanceLosesNothing() {
		assertEquals(0, Interfaces.lossDb(WATER, WATER, 0.67));
	}

	@Test
	void vacuumIsMute() {
		assertEquals(Double.POSITIVE_INFINITY, Interfaces.lossDb(AIR, VACUUM, 0.67));
		assertEquals(Double.POSITIVE_INFINITY, Interfaces.lossDb(VACUUM, VACUUM, 0.67));
	}
}
