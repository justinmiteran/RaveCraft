package com.ravecraft.mod.core.audio.stream;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** At most one stream session per key (a speaker), each owned by the player streaming into it. Not thread-safe. */
public final class SessionTable<K> {
	/** One open session: its id, its key and the player streaming into it. */
	public record Session<K>(int id, K key, UUID owner) {
	}

	/** A session just opened, and the session it replaced on the same key, if any. */
	public record Opened<K>(Session<K> session, Optional<Session<K>> replaced) {
	}

	private final Map<Integer, Session<K>> byId = new HashMap<>();
	private final Map<K, Session<K>> byKey = new HashMap<>();
	private int nextId = 1;

	/**
	 * Opens a session on {@code key}, ending the one already there. Ids are never reused.
	 *
	 * @param key what the session plays on
	 * @param owner the player who opened it
	 * @return the new session and the one it ended
	 */
	public Opened<K> open(K key, UUID owner) {
		Optional<Session<K>> replaced = endByKey(key);
		Session<K> session = new Session<>(nextId++, key, owner);
		byId.put(session.id(), session);
		byKey.put(key, session);
		return new Opened<>(session, replaced);
	}

	/**
	 * The open session with this id.
	 *
	 * @param id the session id
	 * @return the session, or empty
	 */
	public Optional<Session<K>> byId(int id) {
		return Optional.ofNullable(byId.get(id));
	}

	/**
	 * The open session on this key.
	 *
	 * @param key what the session plays on
	 * @return the session, or empty
	 */
	public Optional<Session<K>> byKey(K key) {
		return Optional.ofNullable(byKey.get(key));
	}

	/**
	 * Ends the session with this id and returns it, if it was open.
	 *
	 * @param id the session id
	 * @return the ended session, or empty
	 */
	public Optional<Session<K>> endById(int id) {
		Session<K> session = byId.remove(id);
		if (session != null) {
			byKey.remove(session.key());
		}
		return Optional.ofNullable(session);
	}

	/**
	 * Ends the session on this key and returns it, if one was open.
	 *
	 * @param key what the session plays on
	 * @return the ended session, or empty
	 */
	public Optional<Session<K>> endByKey(K key) {
		Session<K> session = byKey.remove(key);
		if (session != null) {
			byId.remove(session.id());
		}
		return Optional.ofNullable(session);
	}

	/**
	 * Ends every session of {@code owner} and returns them.
	 *
	 * @param owner the player
	 * @return the ended sessions
	 */
	public List<Session<K>> endByOwner(UUID owner) {
		List<Session<K>> ended = new ArrayList<>();
		for (Session<K> session : List.copyOf(byId.values())) {
			if (session.owner().equals(owner)) {
				endById(session.id());
				ended.add(session);
			}
		}
		return ended;
	}

	/**
	 * Ends every session and returns them.
	 *
	 * @return the ended sessions
	 */
	public List<Session<K>> endAll() {
		List<Session<K>> ended = List.copyOf(byId.values());
		byId.clear();
		byKey.clear();
		return ended;
	}
}
