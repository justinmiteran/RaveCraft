package com.ravecraft.mod.core.acoustics.radiation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BandGainsTest {
	@Test
	void relativeNeverExceedsOne() {
		BandGains gains = new BandGains(0.99, 1.13, 0.95).relative();
		assertEquals(1, gains.mid(), 1e-12);
		assertEquals(0.99 / 1.13, gains.low(), 1e-12);
		assertEquals(1.13, new BandGains(0.99, 1.13, 0.95).loudest(), 1e-12);
	}

	@Test
	void fadesToFlatAtTheSpeaker() {
		BandGains gains = new BandGains(0.8, 0.4, 0.2);
		assertEquals(BandGains.FLAT, gains.near(0));
		assertEquals(0.7, gains.near(0.5).mid(), 1e-12);
		assertEquals(gains, gains.near(1));
		assertEquals(gains, gains.near(30));
	}
}
