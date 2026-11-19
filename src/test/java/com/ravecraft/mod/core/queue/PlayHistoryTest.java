package com.ravecraft.mod.core.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayHistoryTest {
	@Test
	void popReturnsLastPushed() {
		PlayHistory history = new PlayHistory();
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		history.push(a);
		history.push(b);
		assertEquals(Optional.of(b), history.pop());
		assertEquals(Optional.of(a), history.pop());
		assertTrue(history.pop().isEmpty());
	}

	@Test
	void keepsAtMostFifty() {
		PlayHistory history = new PlayHistory();
		for (int i = 0; i < 60; i++) {
			history.push(UUID.randomUUID());
		}
		for (int i = 0; i < PlayHistory.MAX; i++) {
			assertTrue(history.pop().isPresent());
		}
		assertTrue(history.pop().isEmpty());
	}

	@Test
	void removePurgesId() {
		PlayHistory history = new PlayHistory();
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		history.push(a);
		history.push(b);
		history.push(a);
		history.remove(a);
		assertEquals(Optional.of(b), history.pop());
		assertTrue(history.pop().isEmpty());
	}
}
