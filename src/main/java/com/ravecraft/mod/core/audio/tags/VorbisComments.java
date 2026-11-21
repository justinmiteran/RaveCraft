package com.ravecraft.mod.core.audio.tags;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/** Vorbis comment blocks, as used by FLAC, Ogg Vorbis and Opus. Pure parsing over bytes already in memory. */
final class VorbisComments {
	private VorbisComments() {
	}

	/**
	 * Reads a Vorbis comment body from {@code b}'s position, setting {@code title, artist, album, year, genre} in
	 * {@code out}. {@code b} must be little-endian. Keys are case-insensitive, the first value of a key wins and
	 * {@code DATE} is cut to its first 4 characters. Stops quietly at the first malformed entry.
	 */
	static void read(ByteBuffer b, Map<String, String> out) {
		if (b.remaining() < 4) {
			return;
		}
		long vendor = b.getInt() & 0xFFFFFFFFL;
		if (vendor > b.remaining() || b.remaining() - vendor < 4) {
			return;
		}
		b.position(b.position() + (int) vendor);
		long count = b.getInt() & 0xFFFFFFFFL;
		// Each entry takes at least 4 bytes, so the loop is bounded by the buffer, not by the declared count.
		for (long i = 0; i < count && b.remaining() >= 4; i++) {
			long length = b.getInt() & 0xFFFFFFFFL;
			if (length > b.remaining()) {
				return;
			}
			byte[] raw = new byte[(int) length];
			b.get(raw);
			String entry = new String(raw, StandardCharsets.UTF_8);
			int eq = entry.indexOf('=');
			if (eq <= 0) {
				continue;
			}
			String key = switch (entry.substring(0, eq).toUpperCase(Locale.ROOT)) {
				case "TITLE" -> Id3Tags.TITLE;
				case "ARTIST" -> Id3Tags.ARTIST;
				case "ALBUM" -> Id3Tags.ALBUM;
				case "DATE" -> Id3Tags.YEAR;
				case "GENRE" -> Id3Tags.GENRE;
				default -> null;
			};
			String value = entry.substring(eq + 1).trim();
			if (key != null && !value.isEmpty() && !out.containsKey(key)) {
				out.put(key, Id3Tags.YEAR.equals(key) && value.length() > 4 ? value.substring(0, 4) : value);
			}
		}
	}
}
