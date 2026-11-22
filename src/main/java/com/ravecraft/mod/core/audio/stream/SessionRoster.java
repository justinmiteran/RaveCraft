package com.ravecraft.mod.core.audio.stream;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedSet;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The streams a client is receiving, by session id. A stream is created by its first chunk. Sessions that were
 * explicitly ended are remembered (a bounded number of them) so late chunks do not bring them back; streams that
 * merely went quiet are forgotten, so they resume when chunks come again. Not thread-safe.
 */
public final class SessionRoster<T> {
	private final int rememberedEnds;
	private final Map<Integer, T> streams = new HashMap<>();
	private final SequencedSet<Integer> ended = new LinkedHashSet<>();

	/**
	 * Creates an empty roster.
	 *
	 * @param rememberedEnds how many ended sessions are remembered
	 */
	public SessionRoster(int rememberedEnds) {
		this.rememberedEnds = rememberedEnds;
	}

	/**
	 * The stream of {@code id}, created with {@code create} if new; empty when the session was ended.
	 *
	 * @param id the session id
	 * @param create makes the stream when new
	 * @return the stream, or empty
	 */
	public Optional<T> admit(int id, Supplier<T> create) {
		T stream = streams.get(id);
		if (stream == null) {
			if (ended.contains(id)) {
				return Optional.empty();
			}
			stream = create.get();
			streams.put(id, stream);
		}
		return Optional.of(stream);
	}

	/**
	 * Ends session {@code id} for good and returns its stream, if one was playing.
	 *
	 * @param id the session id
	 * @return the stream, or empty
	 */
	public Optional<T> end(int id) {
		remember(id);
		return Optional.ofNullable(streams.remove(id));
	}

	/**
	 * Removes the streams matching {@code expired} without ending their sessions: a later chunk restarts them.
	 *
	 * @param expired true for a stream to remove
	 * @return the streams removed
	 */
	public List<T> expireIf(Predicate<T> expired) {
		List<T> removed = new ArrayList<>();
		Iterator<Map.Entry<Integer, T>> it = streams.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, T> entry = it.next();
			if (expired.test(entry.getValue())) {
				removed.add(entry.getValue());
				it.remove();
			}
		}
		return removed;
	}

	/**
	 * Forgets everything and returns the streams that were playing.
	 *
	 * @return the streams that were playing
	 */
	public List<T> clear() {
		List<T> all = new ArrayList<>(streams.values());
		streams.clear();
		ended.clear();
		return all;
	}

	private void remember(int id) {
		ended.add(id);
		while (ended.size() > rememberedEnds) {
			ended.removeFirst();
		}
	}
}
