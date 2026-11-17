package com.ravecraft.mod.core.audio.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EmitterPacerTest {
	@Test
	void pacerAllowsInitialLead() {
		EmitterPacer pacer = new EmitterPacer(2_500);
		assertEquals(0, pacer.delayBeforeNextMillis(0, 0));
		assertEquals(0, pacer.delayBeforeNextMillis(0, 2_500));
		assertEquals(100, pacer.delayBeforeNextMillis(0, 2_600));
	}

	@Test
	void pacerHoldsLead() {
		EmitterPacer pacer = new EmitterPacer(2_500);
		long clock = 0;
		long sent = 0;
		while (clock < 60_000) {
			clock += pacer.delayBeforeNextMillis(clock, sent + 100);
			sent += 100;
			clock += 1; // encoding cost
		}
		long lead = sent - clock;
		assertTrue(lead >= 2_400 && lead <= 2_600, "lead " + lead);
	}
}
