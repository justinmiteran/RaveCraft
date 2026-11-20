package com.ravecraft.mod.client.library;

import com.ravecraft.mod.core.audio.tags.TrackInfo;
import com.ravecraft.mod.core.audio.tags.TrackInfoCache;
import com.ravecraft.mod.core.audio.tags.TrackInfoReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.util.Util;

/** Track tag info for the screens: cached, and read off the client thread. */
public final class TrackInfos {
	private static final TrackInfoCache CACHE = new TrackInfoCache();

	private record Result(boolean unchanged, long modified, TrackInfo info) {
	}

	private TrackInfos() {
	}

	/** Client thread only. Empty while the info is still loading. */
	public static Optional<TrackInfo> get(Path track) {
		String key = track.toString();
		TrackInfoCache.Lookup lookup = CACHE.lookup(key, Util.getMillis());
		if (lookup.load()) {
			long known = lookup.knownModified();
			OffThread.run(() -> {
				long modified = Files.getLastModifiedTime(track).toMillis();
				if (modified == known) {
					return new Result(true, modified, null);
				}
				return new Result(false, modified, TrackInfoReader.read(track));
			}, result -> {
				if (result.unchanged()) {
					CACHE.unchanged(key, Util.getMillis());
				} else {
					CACHE.loaded(key, result.modified(), result.info(), Util.getMillis());
				}
			}, () -> CACHE.failed(key, Util.getMillis()));
		}
		return lookup.info();
	}

	/** Any thread. Reads the file directly, bypassing the cache. */
	public static TrackInfo readNow(Path track) {
		return TrackInfoReader.read(track);
	}
}
