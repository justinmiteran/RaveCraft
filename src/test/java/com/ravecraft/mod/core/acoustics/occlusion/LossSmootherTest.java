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

	@Test
	void leavingMuteRampsDownFrom120() {
		LossSmoother s = new LossSmoother(200);
		assertEquals(Double.POSITIVE_INFINITY, s.step(Double.POSITIVE_INFINITY, 10));
		assertEquals(70, s.step(20, 100), 1e-9);
		assertEquals(20, s.step(20, 100), 1e-9);
	}

	@Test
	void nanTargetIsZero() {
		LossSmoother s = new LossSmoother(200);
		assertEquals(0, s.step(Double.NaN, 10), 1e-9);
		s.step(20, 200);
		assertEquals(10, s.step(Double.NaN, 100), 1e-9);
	}

	@Test
	void targetChangeMidRampRestartsFromCurrentValue() {
		LossSmoother s = new LossSmoother(200);
		s.step(0, 10);
		assertEquals(10, s.step(20, 100), 1e-9);
		// From 10 down to 0, over 200 ms again.
		assertEquals(7.5, s.step(0, 50), 1e-9);
		assertEquals(0, s.step(0, 150), 1e-9);
	}
}
