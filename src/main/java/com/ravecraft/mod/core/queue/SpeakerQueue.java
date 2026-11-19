package com.ravecraft.mod.core.queue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/**
 * A speaker's shared queue: ordered entries, the current one and where it was paused. Immutable: every change
 * returns a new queue. Whether it is playing is not part of it (a loaded speaker is always paused).
 *
 * <p>{@code pending} is the shuffled remainder of the current round, used only when {@code shuffle} is on.
 */
public record SpeakerQueue(List<QueueEntry> entries, Optional<UUID> current, long positionMs, RepeatMode repeat,
		boolean shuffle, List<UUID> pending) {
	public static final int MAX_ENTRIES = 200;
	public static final SpeakerQueue EMPTY = new SpeakerQueue(List.of(), Optional.empty(), 0);

	public SpeakerQueue {
		entries = List.copyOf(entries.size() > MAX_ENTRIES ? entries.subList(0, MAX_ENTRIES) : entries);
		positionMs = Math.max(0, positionMs);
		Objects.requireNonNull(repeat, "repeat");
		pending = List.copyOf(pending);
	}

	public SpeakerQueue(List<QueueEntry> entries, Optional<UUID> current, long positionMs) {
		this(entries, current, positionMs, RepeatMode.ALL, false, List.of());
	}

	/** Appends {@code added} in order, skipping ids already queued and anything past {@link #MAX_ENTRIES}. */
	public SpeakerQueue add(List<QueueEntry> added) {
		return add(added, new Random(0));
	}

	/** As {@link #add(List)}; with shuffle on, each new id also lands at a random spot of the unplayed remainder. */
	public SpeakerQueue add(List<QueueEntry> added, RandomGenerator rng) {
		List<UUID> newPending = new ArrayList<>(pending);
		List<QueueEntry> list = new ArrayList<>(entries);
		Set<UUID> ids = new HashSet<>();
		entries.forEach(e -> ids.add(e.entryId()));
		for (QueueEntry entry : added) {
			if (list.size() >= MAX_ENTRIES) {
				break;
			}
			if (ids.add(entry.entryId())) {
				list.add(entry);
				if (shuffle) {
					newPending.add(rng.nextInt(newPending.size() + 1), entry.entryId());
				}
			}
		}
		return new SpeakerQueue(list, current, positionMs, repeat, shuffle, newPending);
	}

	/**
	 * The entry to play after the current one, or empty to stop. {@code natural} is true when the track ended by
	 * itself (repeat-one then replays it), false for a manual skip. Does not change the queue: see
	 * {@link #afterAdvance}.
	 */
	public Optional<QueueEntry> next(Predicate<UUID> online, boolean natural, RandomGenerator rng) {
		Optional<QueueEntry> now = current.flatMap(this::entry);
		if (repeat == RepeatMode.ONE && natural && now.isPresent() && online.test(now.get().owner())) {
			return now;
		}
		for (QueueEntry candidate : upcoming()) {
			if (online.test(candidate.owner())) {
				return Optional.of(candidate);
			}
		}
		if (repeat == RepeatMode.OFF) {
			return Optional.empty();
		}
		if (shuffle) {
			List<QueueEntry> fresh = new ArrayList<>(entries);
			Collections.shuffle(fresh, rng);
			Optional<QueueEntry> other = fresh.stream()
					.filter(e -> online.test(e.owner()) && !Optional.of(e.entryId()).equals(current))
					.findFirst();
			if (other.isPresent()) {
				return other;
			}
			return now.filter(e -> online.test(e.owner()));
		}
		int index = indexOfCurrent();
		for (int i = 0; i <= index; i++) {
			if (online.test(entries.get(i).owner())) {
				return Optional.of(entries.get(i));
			}
		}
		return Optional.empty();
	}

	/** The queue once {@code nextId} becomes current from its start, with the shuffle round updated. */
	public SpeakerQueue afterAdvance(UUID nextId, RandomGenerator rng) {
		List<UUID> newPending = List.of();
		if (shuffle) {
			if (pending.contains(nextId)) {
				List<UUID> rest = new ArrayList<>(pending);
				rest.remove(nextId);
				newPending = rest;
			} else {
				newPending = shuffledIdsExcept(nextId, rng);
			}
		}
		return new SpeakerQueue(entries, Optional.of(nextId), 0, repeat, shuffle, newPending);
	}

	public SpeakerQueue withRepeat(RepeatMode mode) {
		return new SpeakerQueue(entries, current, positionMs, mode, shuffle, pending);
	}

	/** Turning shuffle on draws a round of everything but the current entry; turning it off drops the round. */
	public SpeakerQueue withShuffle(boolean on, RandomGenerator rng) {
		List<UUID> newPending = on ? shuffledIdsExcept(current.orElse(null), rng) : List.of();
		return new SpeakerQueue(entries, current, positionMs, repeat, on, newPending);
	}

	/** The current entry, then what plays next, then (repeat all only) the entries already played this round. */
	public List<QueueEntry> displayOrder() {
		List<QueueEntry> order = new ArrayList<>();
		current.flatMap(this::entry).ifPresent(order::add);
		order.addAll(upcoming());
		if (repeat == RepeatMode.ALL) {
			order.addAll(played());
		}
		return order;
	}

	/** Index in {@link #displayOrder()} where the next round starts, or -1 when there is no separator. */
	public int nextRoundIndex() {
		if (repeat != RepeatMode.ALL || played().isEmpty()) {
			return -1;
		}
		return (current.flatMap(this::entry).isPresent() ? 1 : 0) + upcoming().size();
	}

	/**
	 * The entry to play: the current one if its owner is online, else the first playable one after it, wrapping
	 * round. Without a current entry the scan starts at the head. Empty when no owner is online.
	 */
	public Optional<QueueEntry> nextPlayable(Predicate<UUID> ownerOnline) {
		if (entries.isEmpty()) {
			return Optional.empty();
		}
		int start = Math.max(0, indexOfCurrent());
		for (int i = 0; i < entries.size(); i++) {
			QueueEntry entry = entries.get((start + i) % entries.size());
			if (ownerOnline.test(entry.owner())) {
				return Optional.of(entry);
			}
		}
		return Optional.empty();
	}

	public SpeakerQueue withCurrent(UUID entryId, long positionMs) {
		return new SpeakerQueue(entries, Optional.of(entryId), positionMs, repeat, shuffle, pending);
	}

	public SpeakerQueue withPosition(long positionMs) {
		return new SpeakerQueue(entries, current, positionMs, repeat, shuffle, pending);
	}

	/**
	 * Removes an entry. Removing the current one makes the entry that followed it current (wrapping to the head),
	 * from its start.
	 */
	public SpeakerQueue remove(UUID entryId) {
		int index = indexOf(entryId);
		if (index < 0) {
			return this;
		}
		List<QueueEntry> list = new ArrayList<>(entries);
		list.remove(index);
		List<UUID> newPending = new ArrayList<>(pending);
		newPending.remove(entryId);
		if (!current.map(entryId::equals).orElse(false)) {
			return new SpeakerQueue(list, current, positionMs, repeat, shuffle, newPending);
		}
		Optional<UUID> following = list.isEmpty()
				? Optional.empty()
				: Optional.of(list.get(index % list.size()).entryId());
		return new SpeakerQueue(list, following, 0, repeat, shuffle, newPending);
	}

	/** Empties the queue, keeping the repeat and shuffle modes. */
	public SpeakerQueue clear() {
		return new SpeakerQueue(List.of(), Optional.empty(), 0, repeat, shuffle, List.of());
	}

	public Optional<QueueEntry> entry(UUID entryId) {
		int index = indexOf(entryId);
		return index < 0 ? Optional.empty() : Optional.of(entries.get(index));
	}

	/** What is still to play this round, in order. */
	private List<QueueEntry> upcoming() {
		List<QueueEntry> list = new ArrayList<>();
		if (shuffle) {
			for (UUID id : pending) {
				entry(id).ifPresent(list::add);
			}
		} else {
			list.addAll(entries.subList(indexOfCurrent() + 1, entries.size()));
		}
		return list;
	}

	/** What already played this round, in list order, current excluded. */
	private List<QueueEntry> played() {
		List<QueueEntry> list = new ArrayList<>();
		if (shuffle) {
			for (QueueEntry e : entries) {
				if (!Optional.of(e.entryId()).equals(current) && !pending.contains(e.entryId())) {
					list.add(e);
				}
			}
		} else {
			list.addAll(entries.subList(0, Math.max(0, indexOfCurrent())));
		}
		return list;
	}

	private List<UUID> shuffledIdsExcept(UUID excluded, RandomGenerator rng) {
		List<UUID> ids = new ArrayList<>();
		for (QueueEntry e : entries) {
			if (!e.entryId().equals(excluded)) {
				ids.add(e.entryId());
			}
		}
		Collections.shuffle(ids, rng);
		return ids;
	}

	private int indexOfCurrent() {
		return current.map(this::indexOf).orElse(-1);
	}

	private int indexOf(UUID entryId) {
		for (int i = 0; i < entries.size(); i++) {
			if (entries.get(i).entryId().equals(entryId)) {
				return i;
			}
		}
		return -1;
	}
}
