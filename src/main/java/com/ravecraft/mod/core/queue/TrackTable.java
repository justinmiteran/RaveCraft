package com.ravecraft.mod.core.queue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A player's local map from queue entry ids to the files they stand for. It never leaves the player's PC. Holds at
 * most {@link #MAX_ENTRIES}; the oldest entries are forgotten first.
 */
public final class TrackTable {
	public static final int MAX_ENTRIES = 5000;

	private final LinkedHashMap<UUID, String> paths = new LinkedHashMap<>() {
		@Override
		protected boolean removeEldestEntry(Map.Entry<UUID, String> eldest) {
			return size() > MAX_ENTRIES;
		}
	};

	/**
	 * A table holding {@code ordered}, oldest first.
	 *
	 * @param ordered the file of each entry, oldest first
	 * @return the table
	 */
	public static TrackTable of(Map<UUID, String> ordered) {
		TrackTable table = new TrackTable();
		ordered.forEach(table::put);
		return table;
	}

	/**
	 * Records the file of {@code entryId}.
	 *
	 * @param entryId the entry
	 * @param path the entry's file
	 */
	public void put(UUID entryId, String path) {
		paths.put(entryId, path);
	}

	/**
	 * The file of {@code entryId}, if known.
	 *
	 * @param entryId the entry
	 * @return the file, or empty
	 */
	public Optional<String> get(UUID entryId) {
		return Optional.ofNullable(paths.get(entryId));
	}

	/**
	 * An ordered copy, oldest first.
	 *
	 * @return the file of each entry, oldest first
	 */
	public Map<UUID, String> snapshot() {
		return new LinkedHashMap<>(paths);
	}
}
