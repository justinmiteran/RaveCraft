package com.ravecraft.mod.core.audio.tags;

import java.util.Optional;

/**
 * What a track's file says about itself. Absent text is {@code ""}, an unknown number is 0 and an unknown
 * duration is -1.
 */
public record TrackInfo(String title, String artist, String album, String year, String genre, String format,
		int bitrateKbps, int sampleRateHz, int channels, long durationMs) {
	public static final int MAX_TEXT = 128;

	/** Trims the text fields, turns null into {@code ""} and cuts them to {@link #MAX_TEXT}. */
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

	/** A track with nothing known but its format. */
	public static TrackInfo empty(String format) {
		return new TrackInfo("", "", "", "", "", format, 0, 0, 0, -1);
	}

	/** {@code artist — title} when both are known, the text a speaker announces to nearby players. */
	public Optional<String> sharedTitle() {
		return title.isEmpty() || artist.isEmpty() ? Optional.empty() : Optional.of(artist + " — " + title);
	}

	/** {@link #sharedTitle()}, otherwise {@code fallback}, cut to {@code max} chars without splitting a surrogate pair. */
	public String queueTitle(String fallback, int max) {
		String title = sharedTitle().orElse(fallback);
		if (title.length() <= max) {
			return title;
		}
		int end = max;
		if (end > 0 && Character.isHighSurrogate(title.charAt(end - 1))) {
			end--;
		}
		return title.substring(0, end);
	}
}
