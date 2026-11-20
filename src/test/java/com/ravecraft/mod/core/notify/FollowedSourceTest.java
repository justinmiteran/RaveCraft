package com.ravecraft.mod.core.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ravecraft.mod.core.notify.FollowedSource.Heard;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FollowedSourceTest {
	private static final long A = 1L;
	private static final long B = 2L;
	private static final OptionalLong NONE = OptionalLong.empty();
	private final UUID e1 = UUID.randomUUID();
	private final UUID e2 = UUID.randomUUID();
	private final FollowedSource source = new FollowedSource();

	private Optional<Heard> at(long ms, Heard... heard) {
		return source.update(ms, List.of(heard), NONE);
	}

	private Heard a1(double gain) {
		return new Heard(A, e1, gain);
	}

	private Heard b2(double gain) {
		return new Heard(B, e2, gain);
	}

	@Test
	void firstLoudestAnnouncedAtOnce() {
		assertEquals(Optional.of(a1(0.5)), at(0, a1(0.5)));
	}

	@Test
	void sameEntryNotAnnouncedTwice() {
		at(0, a1(0.5));
		assertTrue(at(1000, a1(0.5)).isEmpty());
		assertTrue(at(2000, a1(0.4)).isEmpty());
	}

	@Test
	void newTrackOnFollowedAnnounced() {
		at(0, a1(0.5));
		Heard next = new Heard(A, e2, 0.5);
		assertEquals(Optional.of(next), at(1000, next));
	}

	@Test
	void louderSpeakerFollowedAfterDelay() {
		at(0, a1(0.5));
		assertTrue(at(1000, a1(0.2), b2(0.6)).isEmpty());
		assertTrue(at(3999, a1(0.2), b2(0.6)).isEmpty());
		assertEquals(Optional.of(b2(0.6)), at(4000, a1(0.2), b2(0.6)));
	}

	@Test
	void briefPeakDoesNotSwitch() {
		at(0, a1(0.5));
		at(1000, a1(0.2), b2(0.6));
		at(2000, a1(0.7), b2(0.6));
		at(3000, a1(0.2), b2(0.6));
		assertTrue(at(5999, a1(0.2), b2(0.6)).isEmpty());
		assertEquals(Optional.of(b2(0.6)), at(6000, a1(0.2), b2(0.6)));
	}

	@Test
	void openScreenCountsAsAnnounced() {
		assertTrue(source.update(0, List.of(a1(0.5)), OptionalLong.of(A)).isEmpty());
		assertTrue(at(1000, a1(0.5)).isEmpty());
		Heard next = new Heard(A, e2, 0.5);
		assertEquals(Optional.of(next), at(2000, next));
	}

	@Test
	void silentSpeakersIgnored() {
		assertTrue(at(0, a1(0.0)).isEmpty());
		assertEquals(Optional.of(b2(0.3)), at(1000, a1(0.0), b2(0.3)));
	}

	@Test
	void returningToEarlierSpeakerAnnouncesAgain() {
		at(0, a1(0.5));
		at(1000, a1(0.2), b2(0.6));
		assertEquals(Optional.of(b2(0.6)), at(4000, a1(0.2), b2(0.6)));
		at(5000, a1(0.7), b2(0.6));
		assertTrue(at(7999, a1(0.7), b2(0.6)).isEmpty());
		assertEquals(Optional.of(a1(0.7)), at(8000, a1(0.7), b2(0.6)));
	}
}
