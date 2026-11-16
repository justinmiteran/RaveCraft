package com.ravecraft.mod.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CooldownTest {
	@Test
	void blocksUntilTheDelayHasElapsed() {
		Cooldown cooldown = new Cooldown(10);
		assertTrue(cooldown.tryUse(0));
		assertFalse(cooldown.tryUse(9));
		assertTrue(cooldown.tryUse(10));
	}

	@Test
	void rejectsNegativeDelays() {
		assertThrows(IllegalArgumentException.class, () -> new Cooldown(-1));
	}
}
