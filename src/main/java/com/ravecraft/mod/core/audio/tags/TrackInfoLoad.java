package com.ravecraft.mod.core.audio.tags;

import com.ravecraft.mod.core.library.AudioFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** One load of a track's info for the cache: a date check, then a read only when the file changed. Any thread. */
public final class TrackInfoLoad {
	/**
	 * Recorded as the modification date of a file that cannot be read. Distinct from
	 * {@link TrackInfoCache#UNKNOWN_MODIFIED}, so a file unreadable on its first load is still recorded.
	 */
	static final long UNREADABLE = Long.MIN_VALUE;

	/** {@code info} is null when {@code unchanged}. */
	public record Result(boolean unchanged, long modified, TrackInfo info) {
	}

	private TrackInfoLoad() {
	}

	/**
	 * Never throws: a deleted or unreadable file gives an empty info, so its name shows without details.
	 *
	 * @param track the audio file
	 * @param knownModified the modification time already cached
	 * @return the info, or unchanged when the file did not change
	 */
	public static Result load(Path track, long knownModified) {
		long modified;
		try {
			modified = Files.getLastModifiedTime(track).toMillis();
		} catch (IOException e) {
			modified = UNREADABLE;
		}
		if (modified == knownModified) {
			return new Result(true, modified, null);
		}
		if (modified == UNREADABLE) {
			return new Result(false, modified, TrackInfo.empty(AudioFiles.extension(track).toUpperCase(Locale.ROOT)));
		}
		return new Result(false, modified, TrackInfoReader.read(track));
	}
}
