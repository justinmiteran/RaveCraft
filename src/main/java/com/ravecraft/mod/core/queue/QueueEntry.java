package com.ravecraft.mod.core.queue;

import java.util.Objects;
import java.util.UUID;

/** One track in a speaker's shared queue: who added it and what to show. The file path stays on the owner's PC. */
public record QueueEntry(UUID entryId, UUID owner, String ownerName, String title, long durationMs) {
	public static final long UNKNOWN_DURATION = -1;
	public static final int MAX_OWNER_NAME = 16;
	public static final int MAX_TITLE = 128;

	public QueueEntry {
		Objects.requireNonNull(entryId);
		Objects.requireNonNull(owner);
		ownerName = truncate(ownerName, MAX_OWNER_NAME);
		title = truncate(title, MAX_TITLE);
		durationMs = durationMs < 0 ? UNKNOWN_DURATION : durationMs;
	}

	public QueueEntry(UUID entryId, UUID owner, String ownerName, String title) {
		this(entryId, owner, ownerName, title, UNKNOWN_DURATION);
	}

	/** The same entry with its duration set; a negative value means unknown. */
	public QueueEntry withDuration(long durationMs) {
		return new QueueEntry(entryId, owner, ownerName, title, durationMs);
	}

	private static String truncate(String text, int max) {
		Objects.requireNonNull(text);
		return text.length() > max ? text.substring(0, max) : text;
	}
}
