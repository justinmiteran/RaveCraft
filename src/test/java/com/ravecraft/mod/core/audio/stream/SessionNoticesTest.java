package com.ravecraft.mod.core.audio.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionNoticesTest {
	private final UUID p = UUID.randomUUID();
	private final UUID q = UUID.randomUUID();

	@Test
	void eachPlayerOnce() {
		SessionNotices<String> notices = new SessionNotices<>();
		notices.open(1, "a");
		assertEquals(Optional.of("a"), notices.firstFor(1, p));
		assertTrue(notices.firstFor(1, p).isEmpty());
		assertEquals(Optional.of("a"), notices.firstFor(1, q));
	}

	@Test
	void unknownSessionEmpty() {
		SessionNotices<String> notices = new SessionNotices<>();
		assertTrue(notices.firstFor(9, p).isEmpty());
		assertTrue(notices.get(9).isEmpty());
	}

	@Test
	void reopenTellsAgain() {
		SessionNotices<String> notices = new SessionNotices<>();
		notices.open(1, "a");
		notices.firstFor(1, p);
		notices.open(1, "b");
		assertEquals(Optional.of("b"), notices.firstFor(1, p));
	}

	@Test
	void endAndClearForget() {
		SessionNotices<String> notices = new SessionNotices<>();
		notices.open(1, "a");
		notices.open(2, "b");
		notices.end(1);
		assertTrue(notices.get(1).isEmpty());
		assertEquals(Optional.of("b"), notices.get(2));
		notices.clear();
		assertTrue(notices.get(2).isEmpty());
	}

	@Test
	void forgetTellsAgain() {
		SessionNotices<String> notices = new SessionNotices<>();
		notices.open(1, "a");
		notices.open(2, "b");
		notices.firstFor(1, p);
		notices.firstFor(2, p);
		notices.firstFor(1, q);
		notices.forget(p);
		assertEquals(Optional.of("a"), notices.firstFor(1, p));
		assertEquals(Optional.of("b"), notices.firstFor(2, p));
		assertTrue(notices.firstFor(1, q).isEmpty());
	}

	@Test
	void reachedKeepsPlayersToldBeforeReopen() {
		SessionNotices<String> notices = new SessionNotices<>();
		assertTrue(notices.reached(1).isEmpty());
		notices.open(1, "a");
		notices.firstFor(1, p);
		notices.open(1, "b");
		notices.firstFor(1, q);
		assertEquals(Set.of(p, q), notices.reached(1));
		notices.forget(q);
		assertEquals(Set.of(p), notices.reached(1));
		notices.end(1);
		assertTrue(notices.reached(1).isEmpty());
	}
}
