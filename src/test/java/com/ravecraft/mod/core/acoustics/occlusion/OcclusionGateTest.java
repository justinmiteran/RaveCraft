package com.ravecraft.mod.core.acoustics.occlusion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class OcclusionGateTest {
	@Test
	void knownLossIsApplied() {
		assertEquals(0.5 * Math.pow(10, -20.0 / 20), OcclusionGate.gain(0.5, true, 20), 1e-12);
		assertEquals(0.5, OcclusionGate.gain(0.5, true, 0), 1e-12);
	}

	@Test
	void knownMuteIsSilent() {
		assertEquals(0, OcclusionGate.gain(0.5, true, Double.POSITIVE_INFINITY));
	}

	@Test
	void audibleStreamNotYetTracedIsHeldSilent() {
		assertEquals(0, OcclusionGate.gain(0.5, false, 0));
	}

	@Test
	void inaudibleStreamStaysSilent() {
		assertEquals(0, OcclusionGate.gain(0, false, 0));
		assertEquals(0, OcclusionGate.gain(0, true, 12));
	}
}
