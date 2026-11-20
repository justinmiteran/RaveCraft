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

	/** {@code knownModified} is -1 when nothing is cached for the path. */
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

	public Lookup lookup(String path, long nowMs) {
		Cached entry = entries.get(path);
		boolean busy = inFlight.contains(path);
		if (entry == null) {
			if (busy) {
				return new Lookup(Optional.empty(), false, -1);
			}
			inFlight.add(path);
			return new Lookup(Optional.empty(), true, -1);
		}
		boolean due = !busy && nowMs - entry.checkedAt() >= RECHECK_MS;
		if (due) {
			inFlight.add(path);
		}
		return new Lookup(Optional.ofNullable(entry.info()), due, entry.modified());
	}

	public void loaded(String path, long modified, TrackInfo info, long nowMs) {
		inFlight.remove(path);
		entries.put(path, new Cached(info, modified, nowMs));
	}

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
		entries.put(path, entry == null ? new Cached(null, -1, nowMs) : new Cached(entry.info(), entry.modified(), nowMs));
	}
}
