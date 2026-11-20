package com.ravecraft.mod.core.queue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.UUID;

/** The last entries a speaker played, most recent first out, for the "previous" button. Capped at {@link #MAX}. */
public final class PlayHistory {
	public static final int MAX = 50;

	private final Deque<UUID> stack = new ArrayDeque<>();

	public void push(UUID entryId) {
		stack.addFirst(entryId);
		while (stack.size() > MAX) {
			stack.removeLast();
		}
	}

	public Optional<UUID> pop() {
		return Optional.ofNullable(stack.pollFirst());
	}

	/** Purges every occurrence of a removed entry. */
	public void remove(UUID entryId) {
		stack.removeIf(entryId::equals);
	}
}
