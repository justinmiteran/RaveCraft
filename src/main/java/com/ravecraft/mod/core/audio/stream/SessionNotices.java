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
		return Optional.of(notice);
	}

	public Optional<T> get(int sessionId) {
		return Optional.ofNullable(notices.get(sessionId));
	}

	public void end(int sessionId) {
		notices.remove(sessionId);
		told.remove(sessionId);
	}

	public void clear() {
		notices.clear();
		told.clear();
	}
}
