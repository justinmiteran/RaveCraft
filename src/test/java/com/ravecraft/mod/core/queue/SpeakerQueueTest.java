package com.ravecraft.mod.core.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
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

	private static SpeakerQueue at(List<QueueEntry> list, QueueEntry current, RepeatMode repeat) {
		return new SpeakerQueue(list, Optional.of(current.entryId()), 0, repeat, false, List.of());
	}

	@Test
	void repeatAllWrapsToFirst() {
		List<QueueEntry> l = entries(3);
		SpeakerQueue q = at(l, l.get(2), RepeatMode.ALL);
		assertEquals(Optional.of(l.get(0)), q.next(ONLINE, true, new Random(42)));
	}

	@Test
	void repeatOffStopsAfterLast() {
		List<QueueEntry> l = entries(3);
		SpeakerQueue q = at(l, l.get(2), RepeatMode.OFF);
		assertTrue(q.next(ONLINE, true, new Random(42)).isEmpty());
	}

	@Test
	void repeatOneReplaysOnNaturalEnd() {
		List<QueueEntry> l = entries(3);
		SpeakerQueue q = at(l, l.get(1), RepeatMode.ONE);
		assertEquals(Optional.of(l.get(1)), q.next(ONLINE, true, new Random(42)));
	}

	@Test
	void repeatOneNextSkipsAhead() {
		List<QueueEntry> l = entries(3);
		SpeakerQueue q = at(l, l.get(1), RepeatMode.ONE);
		assertEquals(Optional.of(l.get(2)), q.next(ONLINE, false, new Random(42)));
	}

	@Test
	void shuffleVisitsEachOnceBeforeRepeating() {
		Random rng = new Random(42);
		SpeakerQueue q = SpeakerQueue.EMPTY.add(entries(5)).withShuffle(true, rng);
		Set<UUID> seen = new HashSet<>();
		for (int i = 0; i < 5; i++) {
			QueueEntry next = q.next(ONLINE, true, rng).orElseThrow();
			assertTrue(seen.add(next.entryId()));
			q = q.afterAdvance(next.entryId(), rng);
		}
		assertEquals(5, seen.size());
	}

	@Test
	void shuffleAllSingleEntryReplays() {
		Random rng = new Random(42);
		QueueEntry only = entry("a", ON);
		SpeakerQueue q = SpeakerQueue.EMPTY.add(List.of(only)).withCurrent(only.entryId(), 0).withShuffle(true, rng);
		assertEquals(Optional.of(only), q.next(ONLINE, true, rng));
	}

	@Test
	void shuffleAllAbsentStops() {
		Random rng = new Random(42);
		SpeakerQueue q = SpeakerQueue.EMPTY.add(List.of(entry("a", OFF), entry("b", OFF))).withShuffle(true, rng);
		assertTrue(q.next(ONLINE, true, rng).isEmpty());
	}

	@Test
	void addDuringShuffleRoundIsUnplayed() {
		Random rng = new Random(42);
		SpeakerQueue q = SpeakerQueue.EMPTY.add(entries(3)).withShuffle(true, rng);
		QueueEntry added = entry("new", ON);
		q = q.add(List.of(added), rng);
		assertTrue(q.pending().contains(added.entryId()));
		assertEquals(4, q.pending().size());
	}

	@Test
	void removeDropsFromPending() {
		Random rng = new Random(42);
		List<QueueEntry> l = entries(3);
		SpeakerQueue q = SpeakerQueue.EMPTY.add(l).withShuffle(true, rng).remove(l.get(1).entryId());
		assertFalse(q.pending().contains(l.get(1).entryId()));
		assertEquals(2, q.pending().size());
	}

	@Test
	void toggleShuffleKeepsCurrent() {
		Random rng = new Random(42);
		List<QueueEntry> l = entries(4);
		SpeakerQueue q = SpeakerQueue.EMPTY.add(l).withCurrent(l.get(1).entryId(), 700).withShuffle(true, rng);
		assertEquals(Optional.of(l.get(1).entryId()), q.current());
		assertEquals(700, q.positionMs());
		assertEquals(3, q.pending().size());
		assertFalse(q.pending().contains(l.get(1).entryId()));
		SpeakerQueue off = q.withShuffle(false, rng);
		assertEquals(Optional.of(l.get(1).entryId()), off.current());
		assertTrue(off.pending().isEmpty());
	}

	@Test
	void displayOrderShowsCurrentThenUpcoming() {
		List<QueueEntry> l = entries(4);
		SpeakerQueue q = at(l, l.get(1), RepeatMode.ALL);
		assertEquals(List.of(l.get(1), l.get(2), l.get(3), l.get(0)), q.displayOrder());
		assertEquals(3, q.nextRoundIndex());
	}

	@Test
	void displayOrderOffHasNoSeparator() {
		List<QueueEntry> l = entries(4);
		SpeakerQueue q = at(l, l.get(1), RepeatMode.OFF);
		assertEquals(List.of(l.get(1), l.get(2), l.get(3)), q.displayOrder());
		assertEquals(-1, q.nextRoundIndex());
	}

	@Test
	void clearKeepsModes() {
		Random rng = new Random(42);
		SpeakerQueue q = SpeakerQueue.EMPTY.add(entries(2)).withRepeat(RepeatMode.ONE).withShuffle(true, rng).clear();
		assertEquals(RepeatMode.ONE, q.repeat());
		assertTrue(q.shuffle());
		assertTrue(q.entries().isEmpty());
		assertTrue(q.pending().isEmpty());
	}

	@Test
	void nextSkipsAbsentOwners() {
		QueueEntry a = entry("a", ON);
		QueueEntry b = entry("b", OFF);
		QueueEntry c = entry("c", ON);
		SpeakerQueue q = at(List.of(a, b, c), a, RepeatMode.OFF);
		assertEquals(Optional.of(c), q.next(ONLINE, true, new Random(42)));
	}

	@Test
	void removeCurrentUnderShuffleKeepsPendingClean() {
		Random rng = new Random(42);
		List<QueueEntry> l = entries(4);
		SpeakerQueue q = SpeakerQueue.EMPTY.add(l).withCurrent(l.get(1).entryId(), 0).withShuffle(true, rng);
		q = q.remove(l.get(1).entryId());
		assertFalse(q.pending().contains(q.current().orElseThrow()));
		List<UUID> ids = q.displayOrder().stream().map(QueueEntry::entryId).toList();
		assertEquals(ids.size(), new HashSet<>(ids).size());
	}

	@Test
	void repeatOneReplayKeepsShuffleRound() {
		Random rng = new Random(42);
		SpeakerQueue q = SpeakerQueue.EMPTY.add(entries(4)).withRepeat(RepeatMode.ONE).withShuffle(true, rng);
		q = q.afterAdvance(q.pending().get(0), rng);
		SpeakerQueue replay = q.afterAdvance(q.current().orElseThrow(), rng);
		assertEquals(q.pending(), replay.pending());
		assertEquals(0, replay.positionMs());
	}

	@Test
	void withCurrentDropsFromPending() {
		Random rng = new Random(42);
		List<QueueEntry> l = entries(3);
		SpeakerQueue q = SpeakerQueue.EMPTY.add(l).withShuffle(true, rng).withCurrent(l.get(2).entryId(), 0);
		assertFalse(q.pending().contains(l.get(2).entryId()));
		assertEquals(2, q.pending().size());
	}

	@Test
	void nextPlayableFollowsShuffleRound() {
		Random rng = new Random(42);
		QueueEntry off = entry("off", OFF);
		List<QueueEntry> l = new ArrayList<>(entries(3));
		l.add(off);
		SpeakerQueue q = SpeakerQueue.EMPTY.add(l).withCurrent(off.entryId(), 0).withShuffle(true, rng);
		assertEquals(q.entry(q.pending().get(0)), q.nextPlayable(ONLINE, rng));
	}

	@Test
	void withDurationSetsEntryDuration() {
		List<QueueEntry> l = entries(2);
		SpeakerQueue q = SpeakerQueue.EMPTY.add(l).withCurrent(l.get(0).entryId(), 5);
		SpeakerQueue updated = q.withDuration(l.get(1).entryId(), 1234);
		assertEquals(1234, updated.entry(l.get(1).entryId()).orElseThrow().durationMs());
		assertEquals(QueueEntry.UNKNOWN_DURATION, updated.entry(l.get(0).entryId()).orElseThrow().durationMs());
		assertEquals(q.current(), updated.current());
		assertEquals(q.positionMs(), updated.positionMs());
		assertEquals(q, q.withDuration(UUID.randomUUID(), 99));
	}

	@Test
	void negativeDurationIsUnknown() {
		QueueEntry e = entry("t", ON).withDuration(-50);
		assertEquals(QueueEntry.UNKNOWN_DURATION, e.durationMs());
		assertEquals(QueueEntry.UNKNOWN_DURATION, entry("t", ON).durationMs());
	}
}
