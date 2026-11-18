package com.ravecraft.mod.core.acoustics.reverb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SendRouteTest {
	@Test
	void staysPutAtFullGain() {
		SendRoute r = new SendRoute();
		r.step(SendRoute.LISTENER, 500);
		r.step(SendRoute.LISTENER, -5);
		assertEquals(0, r.slot());
		assertEquals(1.0, r.gain(), 1e-12);
	}

	@Test
	void fadesOutSwitchesAndFadesIn() {
		SendRoute r = new SendRoute();
		r.step(1, 50);
		assertEquals(0.5, r.gain(), 1e-9);
		assertEquals(0, r.slot());
		r.step(1, 50);
		assertEquals(0.0, r.gain(), 1e-9);
		assertEquals(1, r.slot());
		r.step(1, 100);
		assertEquals(1.0, r.gain(), 1e-9);
		assertEquals(1, r.slot());
	}

	@Test
	void changingTargetMidFadeReturnsSmoothly() {
		SendRoute r = new SendRoute();
		r.step(2, 40);
		assertEquals(0.6, r.gain(), 1e-9);
		r.step(SendRoute.LISTENER, 20);
		assertEquals(0, r.slot());
		assertEquals(0.8, r.gain(), 1e-9);
		r.step(SendRoute.LISTENER, 1000);
		assertEquals(1.0, r.gain(), 1e-9);
	}

	@Test
	void ignoresNonPositiveDt() {
		SendRoute r = new SendRoute();
		r.step(1, 0);
		r.step(1, Double.NaN);
		assertEquals(1.0, r.gain(), 1e-12);
		assertEquals(0, r.slot());
	}
}
