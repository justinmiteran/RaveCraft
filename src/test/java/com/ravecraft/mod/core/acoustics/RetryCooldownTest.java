package com.ravecraft.mod.core.acoustics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RetryCooldownTest {
	@Test
	void allowsTryingAtFirst() {
		assertTrue(new RetryCooldown(5000).canTry(0));
	}

	@Test
	void failureBlocksUntilCooldownElapses() {
		RetryCooldown cooldown = new RetryCooldown(5000);
		cooldown.failed(1000);
		assertFalse(cooldown.canTry(5999));
		assertTrue(cooldown.canTry(6000));
	}
}
