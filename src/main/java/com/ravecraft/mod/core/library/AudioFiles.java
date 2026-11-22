package com.ravecraft.mod.core.library;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/** File-name rules for the music library: which files are tracks and which are playlists. */
public final class AudioFiles {
	public static final Set<String> AUDIO_EXTENSIONS = Set.of("mp3", "ogg", "oga", "flac", "wav");
	public static final Set<String> PLAYLIST_EXTENSIONS = Set.of("m3u", "m3u8");

	private AudioFiles() {
	}

	/**
	 * Whether {@code path} has the extension of a supported audio format.
	 *
	 * @param path the file
	 * @return true for an audio file
	 */
	public static boolean isAudio(Path path) {
		return AUDIO_EXTENSIONS.contains(extension(path));
	}

	/**
	 * Whether {@code path} has the extension of a playlist.
	 *
	 * @param path the file
	 * @return true for a playlist
	 */
	public static boolean isPlaylist(Path path) {
		return PLAYLIST_EXTENSIONS.contains(extension(path));
	}

	/**
	 * Lower-case extension without the dot, or an empty string.
	 *
	 * @param path the file
	 * @return the extension
	 */
	public static String extension(Path path) {
		Path name = path.getFileName();
		if (name == null) {
			return "";
		}
		String text = name.toString();
		int dot = text.lastIndexOf('.');
		return dot <= 0 ? "" : text.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	/**
	 * File name without its extension.
	 *
	 * @param path the file
	 * @return the base name
	 */
	public static String baseName(Path path) {
		Path name = path.getFileName();
		String text = name == null ? path.toString() : name.toString();
		int dot = text.lastIndexOf('.');
		return dot <= 0 ? text : text.substring(0, dot);
	}
}
