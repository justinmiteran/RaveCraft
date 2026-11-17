package com.ravecraft.mod.core.queue;

import java.util.Objects;
import java.util.UUID;

/** One track in a speaker's shared queue: who added it and what to show. The file path stays on the owner's PC. */
public record QueueEntry(UUID entryId, UUID owner, String ownerName, String title) {
	public static final int MAX_OWNER_NAME = 16;
	public static final int MAX_TITLE = 128;

	public QueueEntry {
		Objects.requireNonNull(entryId);
		Objects.requireNonNull(owner);
		ownerName = truncate(ownerName, MAX_OWNER_NAME);
		title = truncate(title, MAX_TITLE);
	}

	private static String truncate(String text, int max) {
		Objects.requireNonNull(text);
		return text.length() > max ? text.substring(0, max) : text;
	}
}
