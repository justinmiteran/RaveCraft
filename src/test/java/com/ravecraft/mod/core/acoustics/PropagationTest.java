package com.ravecraft.mod.core.acoustics;

import static com.ravecraft.mod.core.acoustics.DeviceProfile.PORTABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PropagationTest {
	private static final double EPS = 1e-9;

	@Test
	void inverseDistanceLaw() {
		assertEquals(0.5, Propagation.distanceGain(1, 2), EPS);
		assertEquals(0.25, Propagation.distanceGain(1, 4), EPS);
	}

	@Test
	void fullGainInsideOneBlock() {
		assertEquals(1, Propagation.distanceGain(1, 0.3), EPS);
		assertEquals(1, Propagation.distanceGain(2, 0.0), EPS);
	}

	@Test
	void rolloffExponent() {
		assertEquals(0.5, Propagation.distanceGain(0.5, 4), EPS);
		assertEquals(0.25, Propagation.distanceGain(2, 2), EPS);
	}

	@Test
	void rangeScalesWithVolume() {
		assertEquals(32, Propagation.range(PORTABLE, 1), EPS);
		assertEquals(16, Propagation.range(PORTABLE, 0.5), EPS);
		assertEquals(0, Propagation.range(PORTABLE, 0), EPS);
	}

	@Test
	void fadeStartsAtEightyPercent() {
		assertEquals(1, Propagation.fade(25.6, 32, 0.2), EPS);
		assertEquals(1, Propagation.fade(20, 32, 0.2), EPS);
		assertEquals(0.5, Propagation.fade(28.8, 32, 0.2), 1e-6);
		assertEquals(0, Propagation.fade(32, 32, 0.2), EPS);
		assertEquals(0, Propagation.fade(40, 32, 0.2), EPS);
	}

	@Test
	void gainZeroBeyondRangeAndAtVolumeZero() {
		assertEquals(0, Propagation.gain(1, 0.2, PORTABLE, 1, 33, 1), EPS);
		assertEquals(0, Propagation.gain(1, 0.2, PORTABLE, 0, 0.5, 1), EPS);
	}

	@Test
	void gainCombinesVolumeAndDistance() {
		assertEquals(0.125, Propagation.gain(1, 0.2, PORTABLE, 0.5, 4, 1), EPS);
	}

	@Test
	void airAbsorption() {
		assertEquals(1, Propagation.highFrequencyGain(0.05, 0), EPS);
		assertEquals(-1.6, 20 * Math.log10(Propagation.highFrequencyGain(0.05, 32)), EPS);
		assertEquals(0.05, Propagation.highFrequencyGain(1, 1000), EPS);
	}

	@Test
	void relayRadius() {
		assertEquals(40, Propagation.relayRadius(PORTABLE, 1), EPS);
		assertEquals(24, Propagation.relayRadius(PORTABLE, 0.5), EPS);
	}

	@Test
	void relayRadiusIsZeroAtVolumeZero() {
		assertEquals(0, Propagation.relayRadius(PORTABLE, 0), EPS);
	}
}
