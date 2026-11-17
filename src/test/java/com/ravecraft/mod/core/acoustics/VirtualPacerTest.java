package com.ravecraft.mod.core.acoustics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VirtualPacerTest {
	@Test
	void countsWholeBlocks() {
		VirtualPacer pacer = new VirtualPacer(0, 100);
		assertEquals(2, pacer.due(250));
		assertEquals(1, pacer.due(300));
		assertEquals(0, pacer.due(350));
	}

	@Test
	void pacerCatchesUpAfterStall() {
		VirtualPacer pacer = new VirtualPacer(0, 100);
		assertEquals(VirtualPacer.MAX_CATCH_UP, pacer.due(5000));
		assertEquals(0, pacer.due(5000));
	}

	@Test
	void resetRestartsClock() {
		VirtualPacer pacer = new VirtualPacer(0, 100);
		pacer.reset(1000);
		assertEquals(1, pacer.due(1150));
	}
}
