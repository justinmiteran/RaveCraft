package com.ravecraft.mod.core.audio.tags;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Remembers the tag info of recently shown tracks, keyed by path. It does no I/O: {@link #lookup} says whether a
 * (re)load is due, and the caller reports back with {@link #loaded}, {@link #unchanged} or {@link #failed}; a failure is retried only after {@link #RECHECK_MS}. Meant
 * for a single thread.
 */
public final class TrackInfoCache {
	public static final int CAPACITY = 512;
	public static final long RECHECK_MS = 5000;
	/** The modification date known for a path with nothing cached. */
	public static final long UNKNOWN_MODIFIED = -1;

	/** {@code knownModified} is {@link #UNKNOWN_MODIFIED} when nothing is cached for the path. */
	public record Lookup(Optional<TrackInfo> info, boolean load, long knownModified) {
	}

	/** {@code info} is null for a path whose load failed and that has nothing cached. */
	private record Cached(TrackInfo info, long modified, long checkedAt) {
	}

	private final Map<String, Cached> entries = new LinkedHashMap<>(64, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
			return size() > CAPACITY;
		}
	};
	private final Set<String> inFlight = new HashSet<>();

	/**
	 * The cached info of {@code path}, and whether the caller should (re)load it now; a load asked for is in
	 * flight until the caller reports back.
	 *
	 * @param path the track's path
	 * @param nowMs the current time
	 * @return the cached info, whether to load, and the modification date the cache knows
	 */
	public Lookup lookup(String path, long nowMs) {
		Cached entry = entries.get(path);
		boolean busy = inFlight.contains(path);
		if (entry == null) {
			if (busy) {
				return new Lookup(Optional.empty(), false, UNKNOWN_MODIFIED);
			}
			inFlight.add(path);
			return new Lookup(Optional.empty(), true, UNKNOWN_MODIFIED);
		}
		boolean due = !busy && nowMs - entry.checkedAt() >= RECHECK_MS;
		if (due) {
			inFlight.add(path);
		}
		return new Lookup(Optional.ofNullable(entry.info()), due, entry.modified());
	}

	/** The cached info, without scheduling anything. */
	public Optional<TrackInfo> peek(String path) {
		Cached entry = entries.get(path);
		return entry == null ? Optional.empty() : Optional.ofNullable(entry.info());
	}

	/** A load finished: caches {@code info} for a file last modified at {@code modified}. */
	public void loaded(String path, long modified, TrackInfo info, long nowMs) {
		inFlight.remove(path);
		entries.put(path, new Cached(info, modified, nowMs));
	}

	/** A re-check found the file unchanged: keeps the cached info and restarts the re-check delay. */
	public void unchanged(String path, long nowMs) {
		inFlight.remove(path);
		Cached entry = entries.get(path);
		if (entry != null) {
			entries.put(path, new Cached(entry.info(), entry.modified(), nowMs));
		}
	}

	/**
	 * A load or re-check failed. The path is not retried before {@link #RECHECK_MS} has passed; any cached info is
	 * kept, and a path with none is remembered as having no info.
	 */
	public void failed(String path, long nowMs) {
		inFlight.remove(path);
		Cached entry = entries.get(path);
		entries.put(path, entry == null ? new Cached(null, UNKNOWN_MODIFIED, nowMs) : new Cached(entry.info(), entry.modified(), nowMs));
	}
}
