package com.ravecraft.mod.core.acoustics.debug.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SoundViewModeTest {
	@Test
	void cycle() {
		assertEquals(SoundViewMode.PATHS, SoundViewMode.NONE.next());
		assertEquals(SoundViewMode.ROOMS, SoundViewMode.PATHS.next());
		assertEquals(SoundViewMode.ALL, SoundViewMode.ROOMS.next());
		assertEquals(SoundViewMode.NONE, SoundViewMode.ALL.next());
	}

	@Test
	void ofFlags() {
		assertEquals(SoundViewMode.NONE, SoundViewMode.of(false, false));
		assertEquals(SoundViewMode.PATHS, SoundViewMode.of(true, false));
		assertEquals(SoundViewMode.ROOMS, SoundViewMode.of(false, true));
		assertEquals(SoundViewMode.ALL, SoundViewMode.of(true, true));
		assertTrue(SoundViewMode.ALL.paths() && SoundViewMode.ALL.rooms());
		assertFalse(SoundViewMode.ROOMS.paths());
		assertFalse(SoundViewMode.PATHS.rooms());
		assertFalse(SoundViewMode.NONE.paths() || SoundViewMode.NONE.rooms());
	}
}
