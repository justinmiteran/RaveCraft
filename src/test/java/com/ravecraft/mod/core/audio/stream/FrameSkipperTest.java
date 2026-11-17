package com.ravecraft.mod.core.audio.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class FrameSkipperTest {
	private static int countSkips(FrameSkipper skipper) {
		int skips = 0;
		while (skipper.skip()) {
			skips++;
		}
		return skips;
	}

	@Test
	void zeroSkipsNothing() {
		assertFalse(new FrameSkipper(0).skip());
	}

	@Test
	void skipsExactFrameCount() {
		FrameSkipper skipper = new FrameSkipper(1000);
		assertEquals(50, countSkips(skipper));
		assertFalse(skipper.skip());
	}

	@Test
	void roundsPartialFrameUp() {
		assertEquals(51, countSkips(new FrameSkipper(1010)));
	}
}
