package com.ravecraft.mod.core.speaker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ravecraft.mod.core.speaker.HostTracker.Expired;
import com.ravecraft.mod.core.speaker.HostTracker.Verdict;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HostTrackerTest {
	private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

	private final HostTracker<String> tracker = new HostTracker<>();

	@Test
	void carrierExpiresAfter20Ticks() {
		tracker.report(ID, "A", 0);
		assertTrue(tracker.expire(19).isEmpty());
		assertEquals(List.of(new Expired<>(ID, "A")), tracker.expire(20));
		assertEquals(Optional.empty(), tracker.host(ID));
	}

	@Test
	void carrierReportingEveryTickNeverExpires() {
		for (long t = 0; t <= 100; t++) {
			tracker.report(ID, "A", t);
		}
		assertTrue(tracker.expire(100).isEmpty());
	}

	@Test
	void placedNeverExpires() {
		tracker.place(ID, "block", 0);
		assertTrue(tracker.expire(10_000).isEmpty());
	}

	@Test
	void lostPlacedExpiresAfter20Ticks() {
		tracker.place(ID, "block", 0);
		tracker.lose(ID, "block", 5);
		assertTrue(tracker.expire(24).isEmpty());
		assertEquals(List.of(new Expired<>(ID, "block")), tracker.expire(25));
	}

	@Test
	void breakThenDropIsMove() {
		tracker.place(ID, "block", 0);
		tracker.lose(ID, "block", 3);
		assertEquals(Verdict.MOVED, tracker.place(ID, "item", 3));
		assertTrue(tracker.expire(100).isEmpty());
	}

	@Test
	void pickupIsMove() {
		tracker.place(ID, "item", 0);
		tracker.lose(ID, "item", 7);
		assertEquals(Verdict.MOVED, tracker.report(ID, "P", 8));
	}

	@Test
	void throwFromCarrierIsMove() {
		tracker.report(ID, "P", 9);
		assertEquals(Verdict.MOVED, tracker.place(ID, "item", 10));
		assertEquals(Optional.of("item"), tracker.host(ID));
	}

	@Test
	void sameCarrierTwiceInOneTickIsDuplicate() {
		assertEquals(Verdict.MOVED, tracker.report(ID, "P", 4));
		assertEquals(Verdict.DUPLICATE, tracker.report(ID, "P", 4));
		assertEquals(Verdict.SAME, tracker.report(ID, "P", 5));
	}

	@Test
	void otherCarrierWhileFirstLiveIsDuplicate() {
		tracker.report(ID, "P", 4);
		assertEquals(Verdict.DUPLICATE, tracker.report(ID, "Q", 5));
		tracker.report(ID, "P", 5);
		assertEquals(Verdict.DUPLICATE, tracker.report(ID, "Q", 5));
		tracker.report(ID, "P", 6);
		assertEquals(Verdict.DUPLICATE, tracker.report(ID, "Q", 6));
		assertEquals(Optional.of("P"), tracker.host(ID));
	}

	@Test
	void secondPlacementIsDuplicate() {
		tracker.place(ID, "b1", 0);
		assertEquals(Verdict.DUPLICATE, tracker.place(ID, "b2", 1));
		assertEquals(Optional.of("b1"), tracker.host(ID));
	}

	@Test
	void carrierOverPlacedIsDuplicate() {
		tracker.place(ID, "block", 0);
		assertEquals(Verdict.DUPLICATE, tracker.report(ID, "P", 1));
	}

	@Test
	void staleLoseIsIgnored() {
		tracker.place(ID, "block", 0);
		tracker.lose(ID, "block", 1);
		assertEquals(Verdict.MOVED, tracker.place(ID, "item", 1));
		tracker.lose(ID, "block", 9);
		assertEquals(Optional.of("item"), tracker.host(ID));
	}
}
