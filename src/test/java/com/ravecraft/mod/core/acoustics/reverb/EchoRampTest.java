package com.ravecraft.mod.core.acoustics.reverb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EchoRampTest {
	private static void run(EchoRamp ramp, Echo target, int steps, double dtMs) {
		for (int i = 0; i < steps; i++) {
			ramp.step(target, dtMs);
		}
	}

	@Test
	void startsFromSilenceAtOnce() {
		EchoRamp ramp = new EchoRamp();
		ramp.step(new Echo(0.2, 0.6), 10);
		assertEquals(0.2, ramp.delaySeconds(), 1e-9);
		assertEquals(0.1, ramp.gain(), 1e-9);
	}

	@Test
	void smallDelayChangeKeepsDelay() {
		EchoRamp ramp = new EchoRamp();
		run(ramp, new Echo(0.2, 0.6), 100, 10);
		ramp.step(new Echo(0.203, 0.5), 10);
		assertEquals(0.2, ramp.delaySeconds(), 1e-9);
		assertEquals(0.5, ramp.gain(), 0.11);
		run(ramp, new Echo(0.203, 0.5), 100, 10);
		assertEquals(0.2, ramp.delaySeconds(), 1e-9);
		assertEquals(0.5, ramp.gain(), 1e-9);
	}

	@Test
	void largeDelayChangeFadesOutSwitchesFadesIn() {
		EchoRamp ramp = new EchoRamp();
		run(ramp, new Echo(0.2, 0.6), 100, 10);
		Echo next = new Echo(0.3, 0.6);
		boolean switched = false;
		for (int i = 0; i < 100; i++) {
			double before = ramp.delaySeconds();
			ramp.step(next, 10);
			if (ramp.delaySeconds() != before) {
				switched = true;
				assertEquals(0, ramp.gain(), 1e-9, "the delay may change only at zero gain");
				assertTrue(i >= 5, "the fade out from 0.6 takes 60 ms");
				assertEquals(0, ramp.gain(), 1e-9);
				assertEquals(0.3, ramp.delaySeconds(), 1e-9);
			}
		}
		assertTrue(switched);
		assertEquals(0.6, ramp.gain(), 1e-9);
	}

	@Test
	void noneKeepsDelayAndFadesOut() {
		EchoRamp ramp = new EchoRamp();
		run(ramp, new Echo(0.2, 0.6), 100, 10);
		run(ramp, Echo.NONE, 3, 10);
		assertEquals(0.2, ramp.delaySeconds(), 1e-9);
		assertTrue(ramp.gain() > 0);
		run(ramp, Echo.NONE, 20, 10);
		assertEquals(0.2, ramp.delaySeconds(), 1e-9);
		assertEquals(0, ramp.gain(), 1e-9);
		assertFalse(ramp.delaySeconds() == 0);
	}
}
