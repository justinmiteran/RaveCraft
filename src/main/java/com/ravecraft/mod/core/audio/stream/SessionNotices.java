package com.ravecraft.mod.core.audio.stream;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * One notice per open session (what it plays), and the players already told about it, so each player hears about a
 * session once. Not thread-safe: the server thread only.
 */
public final class SessionNotices<T> {
	private final Map<Integer, T> notices = new HashMap<>();
	private final Map<Integer, Set<UUID>> told = new HashMap<>();
	/** Everyone told about each session since it first opened, across later notices: who to tell it ended. */
	private final Map<Integer, Set<UUID>> reached = new HashMap<>();

	/** Sets the notice of {@code sessionId}, forgetting who was told about a previous one. */
	public void open(int sessionId, T notice) {
		notices.put(sessionId, notice);
		told.put(sessionId, new HashSet<>());
	}

	/** The notice the first time {@code player} asks about {@code sessionId}; empty afterwards or without one. */
	public Optional<T> firstFor(int sessionId, UUID player) {
		T notice = notices.get(sessionId);
		if (notice == null || !told.get(sessionId).add(player)) {
			return Optional.empty();
		}
		reached.computeIfAbsent(sessionId, id -> new HashSet<>()).add(player);
		return Optional.of(notice);
	}

	public Optional<T> get(int sessionId) {
		return Optional.ofNullable(notices.get(sessionId));
	}

	/** The players told about {@code sessionId} by any of its notices since it opened; a copy. */
	public Set<UUID> reached(int sessionId) {
		return Set.copyOf(reached.getOrDefault(sessionId, Set.of()));
	}

	/** Forgets that {@code player} was told about any session, so a rejoining player is told again. */
	public void forget(UUID player) {
		for (Set<UUID> players : told.values()) {
			players.remove(player);
		}
		for (Set<UUID> players : reached.values()) {
			players.remove(player);
		}
	}

	public void end(int sessionId) {
		notices.remove(sessionId);
		told.remove(sessionId);
		reached.remove(sessionId);
	}

	public void clear() {
		notices.clear();
		told.clear();
		reached.clear();
	}
}
