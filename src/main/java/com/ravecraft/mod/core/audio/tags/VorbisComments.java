package com.ravecraft.mod.core.audio.tags;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/** Vorbis comment blocks, as used by FLAC, Ogg Vorbis and Opus. Pure parsing over bytes already in memory. */
final class VorbisComments {
	private static final int YEAR_LENGTH = 4;

	private VorbisComments() {
	}

	/**
	 * Reads a Vorbis comment body from {@code b}'s position, setting {@code title, artist, album, year, genre} in
	 * {@code out}. {@code b} must be little-endian. Keys are case-insensitive, the first value of a key wins and
	 * {@code DATE} is cut to its first 4 characters. Stops quietly at the first malformed entry.
	 *
	 * @param b the comment body, little-endian
	 * @param out the tags read so far
	 */
	static void read(ByteBuffer b, Map<String, String> out) {
		if (b.remaining() < Integer.BYTES) {
			return;
		}
		long vendor = Integer.toUnsignedLong(b.getInt());
		if (vendor > b.remaining() || b.remaining() - vendor < Integer.BYTES) {
			return;
		}
		b.position(b.position() + (int) vendor);
		long count = Integer.toUnsignedLong(b.getInt());
		// Each entry takes at least 4 bytes, so the loop is bounded by the buffer, not by the declared count.
		boolean more = true;
		for (long i = 0; more && i < count && b.remaining() >= Integer.BYTES; i++) {
			more = entry(b, out);
		}
	}

	/** Reads one {@code KEY=value} entry; false when its length runs past the buffer. */
	private static boolean entry(ByteBuffer b, Map<String, String> out) {
		long length = Integer.toUnsignedLong(b.getInt());
		if (length > b.remaining()) {
			return false;
		}
		byte[] raw = new byte[(int) length];
		b.get(raw);
		String entry = new String(raw, StandardCharsets.UTF_8);
		int eq = entry.indexOf('=');
		if (eq <= 0) {
			return true;
		}
		String key = keyOf(entry.substring(0, eq).toUpperCase(Locale.ROOT));
		String value = entry.substring(eq + 1).trim();
		if (key != null && !value.isEmpty() && !out.containsKey(key)) {
			out.put(key, Id3Tags.YEAR.equals(key) && value.length() > YEAR_LENGTH ? value.substring(0, YEAR_LENGTH)
					: value);
		}
		return true;
	}

	private static String keyOf(String name) {
		return switch (name) {
			case "TITLE" -> Id3Tags.TITLE;
			case "ARTIST" -> Id3Tags.ARTIST;
			case "ALBUM" -> Id3Tags.ALBUM;
			case "DATE" -> Id3Tags.YEAR;
			case "GENRE" -> Id3Tags.GENRE;
			default -> null;
		};
	}
}
