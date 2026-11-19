package com.ravecraft.mod.core.acoustics.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StepTimerTest {
	@Test
	void averagesAndKeepsTheLastSecondsMax() {
		StepTimer timer = new StepTimer();
		timer.record(1_000_000, 0);
		timer.record(3_000_000, 500);
		assertEquals(2, timer.averageMs(), 1e-9);
		assertEquals(3, timer.maxMs(), 1e-9);
		timer.record(1_000_000, 1_000);
		assertEquals(3, timer.maxMs(), 1e-9, "the finished second's max is kept");
		timer.record(1_000_000, 2_000);
		assertEquals(1, timer.maxMs(), 1e-9);
	}

	@Test
	void emptyIsZero() {
		assertEquals(0, new StepTimer().averageMs());
		assertEquals(0, new StepTimer().maxMs());
	}
}
