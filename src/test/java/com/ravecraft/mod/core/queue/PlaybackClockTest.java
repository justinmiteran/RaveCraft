package com.ravecraft.mod.core.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PlaybackClockTest {
	@Test
	void positionAddsElapsed() {
		assertEquals(4000, new PlaybackClock(1000, 50_000).positionAt(53_000));
	}

	@Test
	void positionNeverGoesBackwards() {
		assertEquals(1000, new PlaybackClock(1000, 50_000).positionAt(49_000));
	}
}
