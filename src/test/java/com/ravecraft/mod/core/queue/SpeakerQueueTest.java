package com.ravecraft.mod.core.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class SpeakerQueueTest {
	private static final UUID ON = UUID.randomUUID();
	private static final UUID OFF = UUID.randomUUID();
	private static final Predicate<UUID> ONLINE = Set.of(ON)::contains;

	private static QueueEntry entry(String title, UUID owner) {
		return new QueueEntry(UUID.randomUUID(), owner, "owner", title);
	}

	private static List<QueueEntry> entries(int count) {
		List<QueueEntry> list = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			list.add(entry("t" + i, ON));
		}
		return list;
	}

	@Test
	void addAppendsInOrder() {
		QueueEntry a = entry("a", ON);
		QueueEntry b = entry("b", ON);
		SpeakerQueue q = SpeakerQueue.EMPTY.add(List.of(a)).add(List.of(b));
		assertEquals(List.of(a, b), q.entries());
	}

	@Test
	void addStopsAt200() {
		List<QueueEntry> added = entries(210);
		SpeakerQueue q = SpeakerQueue.EMPTY.add(added);
		assertEquals(200, q.entries().size());
		assertEquals(added.get(199), q.entries().get(199));
	}

	@Test
	void addSkipsDuplicateId() {
		QueueEntry a = entry("a", ON);
		SpeakerQueue q = SpeakerQueue.EMPTY.add(List.of(a)).add(List.of(a, new QueueEntry(a.entryId(), ON, "x", "y")));
		assertEquals(List.of(a), q.entries());
	}

	@Test
	void addKeepsCurrent() {
		QueueEntry a = entry("a", ON);
		SpeakerQueue q = SpeakerQueue.EMPTY.add(List.of(a)).withCurrent(a.entryId(), 300).add(List.of(entry("b", ON)));
		assertEquals(Optional.of(a.entryId()), q.current());
		assertEquals(300, q.positionMs());
	}

	@Test
	void nextPlayableReturnsCurrentWhenOnline() {
		QueueEntry a = entry("a", ON);
		QueueEntry b = entry("b", ON);
		SpeakerQueue q = SpeakerQueue.EMPTY.add(List.of(a, b)).withCurrent(b.entryId(), 0);
		assertEquals(Optional.of(b), q.nextPlayable(ONLINE));
	}

	@Test
	void nextPlayableSkipsAbsent() {
		QueueEntry a = entry("a", OFF);
		QueueEntry b = entry("b", ON);
		SpeakerQueue q = SpeakerQueue.EMPTY.add(List.of(a, b)).withCurrent(a.entryId(), 0);
		assertEquals(Optional.of(b), q.nextPlayable(ONLINE));
	}

	@Test
	void nextPlayableWraps() {
		QueueEntry a = entry("a", ON);
		QueueEntry b = entry("b", OFF);
		QueueEntry c = entry("c", OFF);
		SpeakerQueue q = SpeakerQueue.EMPTY.add(List.of(a, b, c)).withCurrent(b.entryId(), 0);
		assertEquals(Optional.of(a), q.nextPlayable(ONLINE));
	}

	@Test
	void onlyAbsentLeftPauses() {
		SpeakerQueue q = SpeakerQueue.EMPTY.add(List.of(entry("a", OFF), entry("b", OFF)));
		assertTrue(q.nextPlayable(ONLINE).isEmpty());
		assertTrue(SpeakerQueue.EMPTY.nextPlayable(ONLINE).isEmpty());
	}

	@Test
	void nextPlayableWithoutCurrentScansFromStart() {
		QueueEntry a = entry("a", OFF);
		QueueEntry b = entry("b", ON);
		QueueEntry c = entry("c", ON);
		assertEquals(Optional.of(b), SpeakerQueue.EMPTY.add(List.of(a, b, c)).nextPlayable(ONLINE));
	}

	@Test
	void removeCurrentMovesToFollowing() {
		QueueEntry a = entry("a", ON);
		QueueEntry b = entry("b", ON);
		QueueEntry c = entry("c", ON);
		SpeakerQueue q = SpeakerQueue.EMPTY.add(List.of(a, b, c)).withCurrent(b.entryId(), 5000).remove(b.entryId());
		assertEquals(List.of(a, c), q.entries());
		assertEquals(Optional.of(c.entryId()), q.current());
		assertEquals(0, q.positionMs());
	}

	@Test
	void removeLastCurrentWrapsToFirst() {
		QueueEntry a = entry("a", ON);
		QueueEntry b = entry("b", ON);
		SpeakerQueue q = SpeakerQueue.EMPTY.add(List.of(a, b)).withCurrent(b.entryId(), 10).remove(b.entryId());
		assertEquals(Optional.of(a.entryId()), q.current());
		SpeakerQueue alone = SpeakerQueue.EMPTY.add(List.of(a)).withCurrent(a.entryId(), 10).remove(a.entryId());
		assertTrue(alone.current().isEmpty());
		assertTrue(alone.entries().isEmpty());
	}

	@Test
	void removeOtherKeepsCurrentAndPosition() {
		QueueEntry a = entry("a", ON);
		QueueEntry b = entry("b", ON);
		SpeakerQueue q = SpeakerQueue.EMPTY.add(List.of(a, b)).withCurrent(b.entryId(), 700).remove(a.entryId());
		assertEquals(List.of(b), q.entries());
		assertEquals(Optional.of(b.entryId()), q.current());
		assertEquals(700, q.positionMs());
	}

	@Test
	void removeUnknownIsNoOp() {
		SpeakerQueue q = SpeakerQueue.EMPTY.add(List.of(entry("a", ON)));
		assertEquals(q, q.remove(UUID.randomUUID()));
	}

	@Test
	void clearEmpties() {
		QueueEntry a = entry("a", ON);
		assertEquals(SpeakerQueue.EMPTY, SpeakerQueue.EMPTY.add(List.of(a)).withCurrent(a.entryId(), 5).clear());
	}

	@Test
	void titleAndOwnerNameAreTruncated() {
		QueueEntry e = new QueueEntry(UUID.randomUUID(), ON, "n".repeat(40), "t".repeat(300));
		assertEquals(QueueEntry.MAX_OWNER_NAME, e.ownerName().length());
		assertEquals(QueueEntry.MAX_TITLE, e.title().length());
		assertEquals(16, QueueEntry.MAX_OWNER_NAME);
		assertEquals(128, QueueEntry.MAX_TITLE);
	}
}
