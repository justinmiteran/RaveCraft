package com.ravecraft.mod.core.acoustics.radiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ravecraft.mod.core.acoustics.Decibels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PistonOnSphereTest {
	@Test
	void isOneOnAxis() {
		assertEquals(1, new PistonOnSphere(0.13, 0.48, 1000).magnitude(1), 1e-9);
	}

	@Test
	void bassGoesEverywhere() {
		PistonOnSphere bass = new PistonOnSphere(0.05, 0.1, 20);
		for (double cos = -1; cos <= 1; cos += 0.1) {
			assertEquals(0, Decibels.fromAmplitude(bass.magnitude(cos)), 0.2);
		}
	}

	@Test
	void higherFrequenciesAreMoreDirected() {
		double previous = 1;
		for (double hz : new double[] {100, 500, 2000, 8000}) {
			double side = new PistonOnSphere(0.13, 0.48, hz).magnitude(0);
			assertTrue(side < previous, hz + " Hz");
			previous = side;
		}
	}

	@ParameterizedTest
	@ValueSource(doubles = {0.05, 0.1, 0.5, 1, 2})
	void isFiniteOverTheWholeRange(double radius) {
		for (double hz = 20; hz <= 20_000; hz *= 1.5) {
			PistonOnSphere piston = new PistonOnSphere(radius / 4, radius, hz);
			assertTrue(Double.isFinite(piston.real(-1)) && Double.isFinite(piston.imaginary(-1)), hz + " Hz");
		}
	}

	@Test
	void aPistonAsLargeAsTheSphereIsAccepted() {
		assertTrue(Double.isFinite(new PistonOnSphere(0.5, 0.4, 500).magnitude(-1)));
	}
}
