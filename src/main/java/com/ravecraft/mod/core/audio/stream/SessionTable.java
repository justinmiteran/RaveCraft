package com.ravecraft.mod.core.audio.stream;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** At most one stream session per key (a speaker), each owned by the player streaming into it. Not thread-safe. */
public final class SessionTable<K> {
	public record Session<K>(int id, K key, UUID owner) {
	}

	public record Opened<K>(Session<K> session, Optional<Session<K>> replaced) {
	}

	private final Map<Integer, Session<K>> byId = new HashMap<>();
	private final Map<K, Session<K>> byKey = new HashMap<>();
	private int nextId = 1;

	/** Opens a session on {@code key}, ending the one already there. Ids are never reused. */
	public Opened<K> open(K key, UUID owner) {
		Optional<Session<K>> replaced = endByKey(key);
		Session<K> session = new Session<>(nextId++, key, owner);
		byId.put(session.id(), session);
		byKey.put(key, session);
		return new Opened<>(session, replaced);
	}

	public Optional<Session<K>> byId(int id) {
		return Optional.ofNullable(byId.get(id));
	}

	public Optional<Session<K>> byKey(K key) {
		return Optional.ofNullable(byKey.get(key));
	}

	public Optional<Session<K>> endById(int id) {
		Session<K> session = byId.remove(id);
		if (session != null) {
			byKey.remove(session.key());
		}
		return Optional.ofNullable(session);
	}

	public Optional<Session<K>> endByKey(K key) {
		Session<K> session = byKey.remove(key);
		if (session != null) {
			byId.remove(session.id());
		}
		return Optional.ofNullable(session);
	}

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

	public List<Session<K>> endAll() {
		List<Session<K>> ended = List.copyOf(byId.values());
		byId.clear();
		byKey.clear();
		return ended;
	}
}
