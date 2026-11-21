package com.ravecraft.mod.core.notify;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Which speaker's tracks to announce: the one heard loudest, followed at once the first time, then only once another
 * has stayed loudest for {@link #FOLLOW_DELAY_MS}. The same entry on the same speaker is never announced twice in a
 * row. Mutable, single-threaded.
 */
public final class FollowedSource {
	public static final long FOLLOW_DELAY_MS = 3000;

	/** One speaker heard now: {@code speaker} is any stable key, {@code gain} its heard gain (0: silent). */
	public record Heard(long speaker, UUID entryId, double gain) {
	}

	private OptionalLong followed = OptionalLong.empty();
	private OptionalLong candidate = OptionalLong.empty();
	private long candidateSince;
	private long announcedSpeaker;
	private UUID announcedEntry;

	/**
	 * Takes what is heard at {@code nowMs} and returns the entry to announce now, or empty. While the screen of
	 * {@code openSpeaker} is open, its entry counts as announced without being returned.
	 */
	public Optional<Heard> update(long nowMs, List<Heard> heard, OptionalLong openSpeaker) {
		track(nowMs, loudest(heard));
		if (switches(nowMs)) {
			followed = candidate;
		}
		if (followed.isEmpty()) {
			return Optional.empty();
		}
		Heard playing = audible(heard, followed.getAsLong());
		if (playing == null || playing.speaker() == announcedSpeaker && playing.entryId().equals(announcedEntry)) {
			return Optional.empty();
		}
		announcedSpeaker = playing.speaker();
		announcedEntry = playing.entryId();
		boolean open = openSpeaker.isPresent() && openSpeaker.getAsLong() == playing.speaker();
		return open ? Optional.empty() : Optional.of(playing);
	}

	/** Makes {@code loudest} the candidate, timing it from {@code nowMs} when it changes. */
	private void track(long nowMs, Heard loudest) {
		if (loudest == null) {
			candidate = OptionalLong.empty();
		} else if (candidate.isEmpty() || candidate.getAsLong() != loudest.speaker()) {
			candidate = OptionalLong.of(loudest.speaker());
			candidateSince = nowMs;
		}
	}

	/** Whether to follow the candidate now: at once when nothing is followed, else once it lasted the delay. */
	private boolean switches(long nowMs) {
		if (candidate.isEmpty()) {
			return false;
		}
		if (followed.isEmpty()) {
			return true;
		}
		return followed.getAsLong() != candidate.getAsLong() && nowMs - candidateSince >= FOLLOW_DELAY_MS;
	}

	/** The loudest audible speaker, ties going to the smaller key; null when nothing is audible. */
	private static Heard loudest(List<Heard> heard) {
		Heard best = null;
		for (Heard h : heard) {
			if (h.gain() > 0 && louder(h, best)) {
				best = h;
			}
		}
		return best;
	}

	private static boolean louder(Heard h, Heard best) {
		return best == null || h.gain() > best.gain() || h.gain() == best.gain() && h.speaker() < best.speaker();
	}

	private static Heard audible(List<Heard> heard, long speaker) {
		for (Heard h : heard) {
			if (h.speaker() == speaker && h.gain() > 0) {
				return h;
			}
		}
		return null;
	}
}
