package com.ravecraft.mod.core.audio.tags;

import java.util.Optional;

/**
 * What a track's file says about itself. Absent text is {@code ""}, an unknown number is 0 and an unknown
 * duration is -1.
 */
public record TrackInfo(String title, String artist, String album, String year, String genre, String format,
		int bitrateKbps, int sampleRateHz, int channels, long durationMs) {
	public static final int MAX_TEXT = 128;

	public TrackInfo {
		title = clean(title);
		artist = clean(artist);
		album = clean(album);
		year = clean(year);
		genre = clean(genre);
		format = clean(format);
	}

	private static String clean(String s) {
		if (s == null) {
			return "";
		}
		String t = s.trim();
		return t.length() > MAX_TEXT ? t.substring(0, MAX_TEXT) : t;
	}

	public static TrackInfo empty(String format) {
		return new TrackInfo("", "", "", "", "", format, 0, 0, 0, -1);
	}

	/** {@code artist — title} when both are known, the text a speaker announces to nearby players. */
	public Optional<String> sharedTitle() {
		return title.isEmpty() || artist.isEmpty() ? Optional.empty() : Optional.of(artist + " — " + title);
	}
}
