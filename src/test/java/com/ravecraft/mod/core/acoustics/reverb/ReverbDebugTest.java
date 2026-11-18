package com.ravecraft.mod.core.acoustics.reverb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class ReverbDebugTest {
	private static final ReverbParams P = new ReverbParams(1.35, 0.9, 0.32, 0.3, 0.02, 0.02, 0.5, 0.47, 0.994);

	@Test
	void offSaysSo() {
		assertEquals(List.of("RaveCraft reverb: off"), ReverbDebug.lines(false, null, P, Echo.NONE, null));
	}

	@Test
	void roomShowsTheMeasureTheReverbAndTheEcho() {
		RoomShape shape = new RoomShape(0.125, 4.31, 0.1, 0.1, 0.15);
		assertEquals(List.of(
				"RaveCraft reverb: open 13% | free path 4.3 | absorption 0.10 | irregular 0.15",
				"  decay 1.35 s | diffusion 0.47 | reflections 0.30 | gain 0.32",
				"  echo: 0.15 s, gain 0.20"),
				ReverbDebug.lines(true, shape, P, new Echo(0.15, 0.2), null));
		assertEquals("  echo: none", ReverbDebug.lines(true, shape, P, Echo.NONE, null).get(2));
	}

	@Test
	void notYetMeasured() {
		assertEquals("RaveCraft reverb: measuring...", ReverbDebug.lines(true, null, P, Echo.NONE, null).get(0));
	}

	@Test
	void fluidShowsItsPreset() {
		assertEquals(List.of(
				"RaveCraft reverb: in water (preset)",
				"  decay 1.35 s | diffusion 0.47 | reflections 0.30 | gain 0.32"),
				ReverbDebug.lines(true, null, P, Echo.NONE, "water"));
	}
}
