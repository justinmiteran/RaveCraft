package com.ravecraft.mod.client.library;

import com.ravecraft.mod.core.audio.tags.HoverDwell;
import com.ravecraft.mod.core.audio.tags.TrackInfo;
import com.ravecraft.mod.core.audio.tags.TrackInfoCache;
import com.ravecraft.mod.core.audio.tags.TrackInfoLoad;
import com.ravecraft.mod.core.audio.tags.TrackInfoReader;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import net.minecraft.util.Util;

/** Track tag info for the screens: cached, and read off the client thread. */
public final class TrackInfos {
	private static final TrackInfoCache CACHE = new TrackInfoCache();
	private static final HoverDwell DWELL = new HoverDwell();
	/** One thread for every tag read: hovering rows never starts more than one at a time. */
	private static final Executor LOADER = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "RaveCraft track info");
		thread.setDaemon(true);
		return thread;
	});

	private TrackInfos() {
	}

	/**
	 * Client thread only. Empty while the info is still loading. A load starts only once the same track has been asked
	 * for continuously for {@link HoverDwell#DELAY_MS}; cached info is returned at once.
	 */
	public static Optional<TrackInfo> get(Path track) {
		String key = track.toString();
		long now = Util.getMillis();
		if (!DWELL.ready(key, now)) {
			return CACHE.peek(key);
		}
		TrackInfoCache.Lookup lookup = CACHE.lookup(key, now);
		if (lookup.load()) {
			long known = lookup.knownModified();
			OffThread.run(LOADER, () -> TrackInfoLoad.load(track, known), result -> {
				if (result.unchanged()) {
					CACHE.unchanged(key, Util.getMillis());
				} else {
					CACHE.loaded(key, result.modified(), result.info(), Util.getMillis());
				}
			}, () -> CACHE.failed(key, Util.getMillis()));
		}
		return lookup.info();
	}

	/** Client thread only. The cached info of {@code track}, if any; never starts a load. */
	public static Optional<TrackInfo> cached(Path track) {
		return CACHE.peek(track.toString());
	}

	/** Any thread. Reads the file directly, bypassing the cache. */
	public static TrackInfo readNow(Path track) {
		return TrackInfoReader.read(track);
	}
}
