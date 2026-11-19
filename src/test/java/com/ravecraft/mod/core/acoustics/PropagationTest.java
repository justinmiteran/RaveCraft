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

	/** 20 W at 85 dB/W/m is 98 dB at 1 m; down to the 68 dB threshold that is 30 dB, so 10^1.5 blocks. */
	private static final double PORTABLE_RANGE = Math.pow(10, (85 + 10 * Math.log10(20) - 68) / 20);
	private static final AcousticSettings S = AcousticSettings.DEFAULT;

	private static AcousticSettings with(double rolloff, double thresholdDb, double maxRange) {
		return new AcousticSettings(rolloff, S.fadeFraction(), thresholdDb, maxRange, S.portable(), S.occlusion(), S.reverb());
	}

	@Test
	void levelAtOneMetreFromPowerAndSensitivity() {
		assertEquals(85 + 10 * Math.log10(20), Propagation.levelAt1m(PORTABLE), EPS);
		assertEquals(94, Propagation.levelAt1m(new DeviceProfile(1, 94, -6, 150, Directivity.OMNI)), EPS);
	}

	@Test
	void portableCarriesAboutThirtyTwoBlocks() {
		assertEquals(31.66, Propagation.range(S, PORTABLE, 1), 0.01);
	}

	@Test
	void rangeScalesWithVolume() {
		assertEquals(PORTABLE_RANGE / 2, Propagation.range(S, PORTABLE, 0.5), 1e-6);
		assertEquals(0, Propagation.range(S, PORTABLE, 0), EPS);
	}

	@Test
	void fourTimesThePowerDoublesTheRange() {
		DeviceProfile loud = new DeviceProfile(80, 85, -6, 150, Directivity.OMNI);
		assertEquals(2 * PORTABLE_RANGE, Propagation.range(S, loud, 1), 1e-6);
	}

	@Test
	void softerRolloffCarriesFurther() {
		double base = Propagation.range(with(1, 80, 512), PORTABLE, 1);
		assertEquals(base * base, Propagation.range(with(0.5, 80, 512), PORTABLE, 1), 1e-6);
	}

	@Test
	void rangeIsCapped() {
		DeviceProfile festival = new DeviceProfile(8000, 100, 0, 40, Directivity.OMNI);
		assertEquals(256, Propagation.range(S, festival, 1), EPS);
		assertEquals(100, Propagation.range(with(1, 68, 100), festival, 1), EPS);
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
		assertEquals(0, Propagation.gain(S, PORTABLE, 1, 32, 1), EPS);
		assertEquals(0, Propagation.gain(S, PORTABLE, 0, 0.5, 1), EPS);
	}

	@Test
	void gainCombinesVolumeAndDistance() {
		assertEquals(0.125, Propagation.gain(S, PORTABLE, 0.5, 4, 1), EPS);
	}

	@Test
	void airAbsorption() {
		assertEquals(1, Propagation.highFrequencyGain(0.05, 0), EPS);
		assertEquals(-1.6, 20 * Math.log10(Propagation.highFrequencyGain(0.05, 32)), EPS);
		assertEquals(0.05, Propagation.highFrequencyGain(1, 1000), EPS);
	}

	@Test
	void relayRadius() {
		assertEquals(PORTABLE_RANGE + 8, Propagation.relayRadius(S, PORTABLE, 1), 1e-6);
		assertEquals(PORTABLE_RANGE / 2 + 8, Propagation.relayRadius(S, PORTABLE, 0.5), 1e-6);
	}

	@Test
	void relayRadiusIsZeroAtVolumeZero() {
		assertEquals(0, Propagation.relayRadius(S, PORTABLE, 0), EPS);
	}
}
