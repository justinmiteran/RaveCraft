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

	/**
	 * Sets the notice of {@code sessionId}, forgetting who was told about a previous one.
	 *
	 * @param sessionId the session
	 * @param notice what the session plays
	 */
	public void open(int sessionId, T notice) {
		notices.put(sessionId, notice);
		told.put(sessionId, new HashSet<>());
	}

	/**
	 * The notice the first time {@code player} asks about {@code sessionId}; empty afterwards or without one.
	 *
	 * @param sessionId the session
	 * @param player the player asking
	 * @return the notice, or empty
	 */
	public Optional<T> firstFor(int sessionId, UUID player) {
		T notice = notices.get(sessionId);
		if (notice == null || !told.get(sessionId).add(player)) {
			return Optional.empty();
		}
		return Optional.of(notice);
	}

	/**
	 * The notice of {@code sessionId}, if it is open.
	 *
	 * @param sessionId the session
	 * @return the notice, or empty
	 */
	public Optional<T> get(int sessionId) {
		return Optional.ofNullable(notices.get(sessionId));
	}

	/**
	 * Forgets that {@code player} was told about any session, so a rejoining player is told again.
	 *
	 * @param player the player
	 */
	public void forget(UUID player) {
		for (Set<UUID> players : told.values()) {
			players.remove(player);
		}
	}

	/**
	 * Forgets {@code sessionId} and who was told about it.
	 *
	 * @param sessionId the session
	 */
	public void end(int sessionId) {
		notices.remove(sessionId);
		told.remove(sessionId);
	}

	/** Forgets every session. */
	public void clear() {
		notices.clear();
		told.clear();
	}
}
