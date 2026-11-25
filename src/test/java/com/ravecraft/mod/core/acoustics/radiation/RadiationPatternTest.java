package com.ravecraft.mod.core.acoustics.radiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ravecraft.mod.core.acoustics.Decibels;
import com.ravecraft.mod.core.speaker.SpeakerKind;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RadiationPatternTest {
	private static final RadiationPattern PARTY = RadiationPattern.of(Cabinets.of(SpeakerKind.PARTY));
	private static final RadiationPattern PORTABLE = RadiationPattern.of(Cabinets.of(SpeakerKind.PORTABLE));

	@ParameterizedTest
	@CsvSource({"0, 0, 0, 0", "30, -0.3, -1.3, -2.9", "60, -1.2, -4.0, -7.6", "90, -2.6, -7.6, -12.6",
			"120, -3.5, -11.9, -20.8", "150, -2.8, -15.8, -29.5", "180, -2.2, -11.8, -27.5"})
	void partyMatchesTheReference(double azimuth, double low, double mid, double high) {
		for (double side : new double[] {azimuth, -azimuth}) {
			BandGains gains = PARTY.at(side, 0);
			assertDb(low, gains.low());
			assertDb(mid, gains.mid());
			assertDb(high, gains.high());
		}
	}

	@Test
	void portableIsTheSameAllAround() {
		for (int azimuth = 0; azimuth < 360; azimuth += 5) {
			BandGains gains = PORTABLE.at(azimuth, 0);
			assertEquals(0, Decibels.fromAmplitude(gains.low()), 3);
			assertEquals(0, Decibels.fromAmplitude(gains.mid()), 3);
			assertEquals(0, Decibels.fromAmplitude(gains.high()), 3);
		}
	}

	@Test
	void aRowOfSubsDirectsTheBass() {
		double single = row(1).at(90, 0).low();
		double four = row(4).at(90, 0).low();
		assertEquals(-2.8, Decibels.fromAmplitude(single), 0.5);
		assertEquals(-15.8, Decibels.fromAmplitude(four), 1.5);
	}

	@Test
	void turnedDriversTurnThePattern() {
		Driver driver = new Driver(90, 0, 0, 0, 0, 0.13, 0, 0);
		RadiationPattern pattern = RadiationPattern.of(new Cabinet(0.75, 1, 0.62, List.of(driver)));
		assertTrue(pattern.at(90, 0).high() > 10 * pattern.at(-90, 0).high());
	}

	@Test
	void isContinuousAroundAndAtThePoles() {
		assertEquals(PARTY.at(0, 10), PARTY.at(360, 10));
		assertEquals(PARTY.at(-90, 10), PARTY.at(270, 10));
		assertEquals(PARTY.at(0, 90), PARTY.at(137, 90));
		assertEquals(PARTY.at(359.99, 0).mid(), PARTY.at(0.01, 0).mid(), 1e-3);
		assertEquals(PARTY.at(12, 89.99).mid(), PARTY.at(200, 89.99).mid(), 1e-2);
	}

	@Test
	void neverFallsUnderTheFloor() {
		Driver front = new Driver(0, 0, 0, 0, 0, 1, 0, 0);
		RadiationPattern huge = RadiationPattern.of(new Cabinet(4, 4, 4, List.of(front)));
		assertEquals(-48, Decibels.fromAmplitude(huge.at(150, 0).high()), 1e-6);
	}

	@Test
	void onlyThePartySpeakerHasADirection() {
		assertFalse(PARTY.omni());
		assertTrue(PORTABLE.omni());
		assertTrue(RadiationPattern.FLAT.omni());
	}

	@Test
	void refusesACabinetThatMakesNoSound() {
		Cabinet mute = new Cabinet(1, 1, 1, List.of());
		assertThrows(IllegalArgumentException.class, () -> RadiationPattern.of(mute));
	}

	@Test
	void aroundIsTheMeanPower() {
		assertEquals(-1.8, Decibels.fromAmplitude(PARTY.around().low()), 0.5);
		assertEquals(-4.9, Decibels.fromAmplitude(PARTY.around().mid()), 0.5);
		assertEquals(-6.8, Decibels.fromAmplitude(PARTY.around().high()), 0.5);
		assertEquals(BandGains.FLAT, RadiationPattern.FLAT.around());
	}

	/** {@code count} 46 cm subs side by side, one block apart. */
	private static RadiationPattern row(int count) {
		List<Driver> drivers = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			drivers.add(new Driver(0, 0, i - (count - 1) / 2.0, 0, 0, 0.2, 0, 0));
		}
		return RadiationPattern.of(new Cabinet(0.7, 0.9, 0.8, drivers));
	}

	/** ±0.5 dB, ±1.5 dB under −20 dB. */
	private static void assertDb(double expected, double gain) {
		assertEquals(expected, Decibels.fromAmplitude(gain), expected < -20 ? 1.5 : 0.5);
	}
}
