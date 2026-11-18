package com.ravecraft.mod.core.acoustics.occlusion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LossSmootherTest {
	@Test
	void firstValueJumps() {
		LossSmoother s = new LossSmoother(200);
		assertEquals(30, s.step(30, 10), 1e-9);
	}

	@Test
	void reachesTargetIn200ms() {
		LossSmoother s = new LossSmoother(200);
		assertEquals(0, s.step(0, 10), 1e-9);
		assertEquals(10, s.step(20, 100), 1e-9);
		assertEquals(20, s.step(20, 100), 1e-9);
		assertEquals(20, s.step(20, 100), 1e-9);
	}

	@Test
	void muteRampsThenSilences() {
		LossSmoother s = new LossSmoother(200);
		s.step(0, 10);
		double a = s.step(Double.POSITIVE_INFINITY, 50);
		double b = s.step(Double.POSITIVE_INFINITY, 100);
		assertTrue(Double.isFinite(a) && Double.isFinite(b));
		assertTrue(a > 0 && b > a);
		assertEquals(Double.POSITIVE_INFINITY, s.step(Double.POSITIVE_INFINITY, 50));
	}

	@Test
	void resetJumpsAgain() {
		LossSmoother s = new LossSmoother(200);
		s.step(0, 10);
		s.reset();
		assertEquals(40, s.step(40, 10), 1e-9);
	}
}
