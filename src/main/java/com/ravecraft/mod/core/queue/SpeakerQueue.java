package com.ravecraft.mod.core.queue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * A speaker's shared queue: ordered entries, the current one and where it was paused. Immutable: every change
 * returns a new queue. Whether it is playing is not part of it (a loaded speaker is always paused).
 */
public record SpeakerQueue(List<QueueEntry> entries, Optional<UUID> current, long positionMs) {
	public static final int MAX_ENTRIES = 200;
	public static final SpeakerQueue EMPTY = new SpeakerQueue(List.of(), Optional.empty(), 0);

	public SpeakerQueue {
		entries = List.copyOf(entries.size() > MAX_ENTRIES ? entries.subList(0, MAX_ENTRIES) : entries);
		positionMs = Math.max(0, positionMs);
	}

	/** Appends {@code added} in order, skipping ids already queued and anything past {@link #MAX_ENTRIES}. */
	public SpeakerQueue add(List<QueueEntry> added) {
		List<QueueEntry> list = new ArrayList<>(entries);
		Set<UUID> ids = new HashSet<>();
		entries.forEach(e -> ids.add(e.entryId()));
		for (QueueEntry entry : added) {
			if (list.size() >= MAX_ENTRIES) {
				break;
			}
			if (ids.add(entry.entryId())) {
				list.add(entry);
			}
		}
		return new SpeakerQueue(list, current, positionMs);
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
		return new SpeakerQueue(entries, Optional.of(entryId), positionMs);
	}

	public SpeakerQueue withPosition(long positionMs) {
		return new SpeakerQueue(entries, current, positionMs);
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
		if (!current.map(entryId::equals).orElse(false)) {
			return new SpeakerQueue(list, current, positionMs);
		}
		Optional<UUID> following = list.isEmpty()
				? Optional.empty()
				: Optional.of(list.get(index % list.size()).entryId());
		return new SpeakerQueue(list, following, 0);
	}

	public SpeakerQueue clear() {
		return EMPTY;
	}

	public Optional<QueueEntry> entry(UUID entryId) {
		int index = indexOf(entryId);
		return index < 0 ? Optional.empty() : Optional.of(entries.get(index));
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
