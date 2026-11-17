package com.ravecraft.mod.core.audio.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SessionRosterTest {
	private final SessionRoster<String> roster = new SessionRoster<>(3);

	@Test
	void admitCreatesOnceThenReuses() {
		String first = roster.admit(1, () -> "a").orElseThrow();
		assertSame(first, roster.admit(1, () -> "b").orElseThrow());
	}

	@Test
	void endedSessionIsNotRecreated() {
		roster.admit(1, () -> "a");
		assertEquals("a", roster.end(1).orElseThrow());
		assertTrue(roster.admit(1, () -> "b").isEmpty());
	}

	@Test
	void endBeforeFirstChunkStillBlocksLateChunks() {
		assertTrue(roster.end(7).isEmpty());
		assertTrue(roster.admit(7, () -> "late").isEmpty());
	}

	@Test
	void expiredSessionResumesOnNextChunk() {
		roster.admit(1, () -> "a");
		assertEquals(List.of("a"), roster.expireIf(s -> true));
		assertEquals("b", roster.admit(1, () -> "b").orElseThrow());
	}

	@Test
	void rememberedEndsAreBounded() {
		for (int id = 1; id <= 4; id++) {
			roster.end(id);
		}
		assertEquals("a", roster.admit(1, () -> "a").orElseThrow());
		assertTrue(roster.admit(4, () -> "b").isEmpty());
	}

	@Test
	void clearForgetsEverything() {
		roster.admit(1, () -> "a");
		roster.end(2);
		assertEquals(List.of("a"), roster.clear());
		assertEquals("c", roster.admit(2, () -> "c").orElseThrow());
	}
}
