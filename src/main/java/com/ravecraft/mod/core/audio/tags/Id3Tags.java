package com.ravecraft.mod.core.audio.tags;

import com.ravecraft.mod.core.audio.Bytes;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** ID3v2.3/2.4 and ID3v1 text tags. Pure parsing over bytes already in memory. */
final class Id3Tags {
	static final String TITLE = "title";
	static final String ARTIST = "artist";
	static final String ALBUM = "album";
	static final String YEAR = "year";
	static final String GENRE = "genre";

	private static final List<String> GENRES = List.of("Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk",
			"Grunge", "Hip-Hop", "Jazz", "Metal", "New Age", "Oldies", "Other", "Pop", "R&B", "Rap", "Reggae", "Rock",
			"Techno", "Industrial", "Alternative", "Ska", "Death Metal", "Pranks", "Soundtrack", "Euro-Techno",
			"Ambient", "Trip-Hop", "Vocal", "Jazz+Funk", "Fusion", "Trance", "Classical", "Instrumental", "Acid",
			"House", "Game", "Sound Clip", "Gospel", "Noise", "AlternRock", "Bass", "Soul", "Punk", "Space",
			"Meditative", "Instrumental Pop", "Instrumental Rock", "Ethnic", "Gothic", "Darkwave",
			"Techno-Industrial", "Electronic", "Pop-Folk", "Eurodance", "Dream", "Southern Rock", "Comedy", "Cult",
			"Gangsta", "Top 40", "Christian Rap", "Pop/Funk", "Jungle", "Native American", "Cabaret", "New Wave",
			"Psychedelic", "Rave", "Showtunes", "Trailer", "Lo-Fi", "Tribal", "Acid Punk", "Acid Jazz", "Polka",
			"Retro", "Musical", "Rock & Roll", "Hard Rock");

	private static final int V23 = 3;
	private static final int V24 = 4;
	private static final int FRAME_HEADER = 10;
	private static final int FRAME_SIZE_AT = 4;
	private static final int EXTENDED_SIZE_BYTES = 4;
	private static final int YEAR_LENGTH = 4;
	private static final int ENCODING_LATIN1 = 0;
	private static final int ENCODING_UTF16 = 1;
	private static final int ENCODING_UTF16BE = 2;
	private static final int ENCODING_UTF8 = 3;
	private static final int MAX_GENRE_DIGITS = 3;

	// ID3v1: "TAG", then fixed-width Latin-1 fields and a genre byte, 128 bytes in all.
	private static final int V1_LENGTH = 128;
	private static final int V1_TITLE_AT = 3;
	private static final int V1_ARTIST_AT = 33;
	private static final int V1_ALBUM_AT = 63;
	private static final int V1_YEAR_AT = 93;
	private static final int V1_GENRE_AT = 127;
	private static final int V1_TEXT_LENGTH = 30;
	private static final byte[] V1_MAGIC = {'T', 'A', 'G'};

	private Id3Tags() {
	}

	/** Moves {@code tag} past the extended header, which starts at its position (flag 0x40 of the tag header). */
	static void skipExtendedHeader(ByteBuffer tag, int version) {
		if (tag.remaining() < EXTENDED_SIZE_BYTES) {
			return;
		}
		int at = tag.position();
		// v2.3: the size excludes its own 4 bytes; v2.4: it includes them.
		int size = version == V24 ? Bytes.syncsafe(tag, at) : tag.getInt(at) + EXTENDED_SIZE_BYTES;
		tag.position(size < EXTENDED_SIZE_BYTES || size > tag.remaining() ? tag.limit() : at + size);
	}

	/**
	 * Reads the text frames of an ID3v2.3 or 2.4 tag body from {@code tag}'s position to its limit, setting
	 * {@code title, artist, album, year, genre} in {@code out}. The first value of a key wins. Stops at the first
	 * malformed frame.
	 */
	static void readV2(ByteBuffer tag, int version, Map<String, String> out) {
		if (version != V23 && version != V24) {
			return;
		}
		boolean more = true;
		while (more && tag.remaining() >= FRAME_HEADER) {
			more = readFrame(tag, version, out);
		}
	}

	/** Reads the frame at {@code tag}'s position and moves past it; false when the frame is malformed. */
	private static boolean readFrame(ByteBuffer tag, int version, Map<String, String> out) {
		int at = tag.position();
		String id = frameId(tag, at);
		long size = version == V24 ? Bytes.syncsafe(tag, at + FRAME_SIZE_AT) : Bytes.u32(tag, at + FRAME_SIZE_AT);
		if (id == null || size > tag.remaining() - FRAME_HEADER) {
			return false;
		}
		int body = at + FRAME_HEADER;
		String key = keyOf(id);
		if (key != null && size > 0 && !out.containsKey(key)) {
			out.put(key, value(key, text(tag, body, (int) size)));
		}
		tag.position(body + (int) size);
		return true;
	}

	private static String keyOf(String frameId) {
		return switch (frameId) {
			case "TIT2" -> TITLE;
			case "TPE1" -> ARTIST;
			case "TALB" -> ALBUM;
			case "TDRC", "TYER" -> YEAR;
			case "TCON" -> GENRE;
			default -> null;
		};
	}

	private static String value(String key, String text) {
		return switch (key) {
			case YEAR -> text.length() > YEAR_LENGTH ? text.substring(0, YEAR_LENGTH) : text;
			case GENRE -> genre(text);
			default -> text;
		};
	}

	private static String frameId(ByteBuffer b, int at) {
		char[] id = new char[Bytes.FOUR_CC];
		for (int i = 0; i < Bytes.FOUR_CC; i++) {
			char c = (char) Bytes.u8(b, at + i);
			if (!isIdChar(c)) {
				return null;
			}
			id[i] = c;
		}
		return new String(id);
	}

	private static boolean isIdChar(char c) {
		return c >= 'A' && c <= 'Z' || c >= '0' && c <= '9';
	}

	/** Decodes a text frame body: an encoding byte, then text up to the first terminator of that encoding. */
	private static String text(ByteBuffer b, int at, int size) {
		int encoding = Bytes.u8(b, at);
		Charset cs = charset(encoding);
		if (cs == null) {
			return "";
		}
		int from = at + 1;
		int stop = terminator(b, from, at + size, encoding == ENCODING_UTF16 || encoding == ENCODING_UTF16BE);
		byte[] raw = new byte[stop - from];
		b.get(from, raw);
		return new String(raw, cs).trim();
	}

	/** The position of the first terminator from {@code from}: one zero byte, or two for UTF-16. */
	private static int terminator(ByteBuffer b, int from, int end, boolean wide) {
		int step = wide ? 2 : 1;
		int stop = from;
		while (stop + step <= end && !isTerminator(b, stop, wide)) {
			stop += step;
		}
		return stop;
	}

	private static boolean isTerminator(ByteBuffer b, int at, boolean wide) {
		return b.get(at) == 0 && (!wide || b.get(at + 1) == 0);
	}

	private static Charset charset(int encoding) {
		return switch (encoding) {
			case ENCODING_LATIN1 -> StandardCharsets.ISO_8859_1;
			case ENCODING_UTF16 -> StandardCharsets.UTF_16; // honours the BOM, big-endian without one
			case ENCODING_UTF16BE -> StandardCharsets.UTF_16BE;
			case ENCODING_UTF8 -> StandardCharsets.UTF_8;
			default -> null;
		};
	}

	/** Fills the keys of {@code out} that are still missing from the 128 last bytes of an MP3, when they hold ID3v1. */
	static void readV1(ByteBuffer last128, Map<String, String> out) {
		if (last128.remaining() < V1_LENGTH) {
			return;
		}
		int at = last128.position();
		byte[] magic = new byte[V1_MAGIC.length];
		last128.get(at, magic);
		if (!Arrays.equals(magic, V1_MAGIC)) {
			return;
		}
		out.putIfAbsent(TITLE, v1(last128, at + V1_TITLE_AT, V1_TEXT_LENGTH));
		out.putIfAbsent(ARTIST, v1(last128, at + V1_ARTIST_AT, V1_TEXT_LENGTH));
		out.putIfAbsent(ALBUM, v1(last128, at + V1_ALBUM_AT, V1_TEXT_LENGTH));
		out.putIfAbsent(YEAR, v1(last128, at + V1_YEAR_AT, YEAR_LENGTH));
		out.putIfAbsent(GENRE, genre(Integer.toString(Bytes.u8(last128, at + V1_GENRE_AT))));
	}

	private static String v1(ByteBuffer b, int at, int len) {
		byte[] raw = new byte[len];
		b.get(at, raw);
		int n = 0;
		while (n < len && raw[n] != 0) {
			n++;
		}
		return new String(raw, 0, n, StandardCharsets.ISO_8859_1).trim();
	}

	/** Resolves {@code (n)} and {@code n} against the 80 standard genres; other text is kept, bad numbers are empty. */
	static String genre(String raw) {
		String s = raw.trim();
		if (s.startsWith("(") && s.indexOf(')') > 0) {
			String rest = s.substring(s.indexOf(')') + 1).trim();
			String number = s.substring(1, s.indexOf(')'));
			if (isNumber(number)) {
				return numbered(number);
			}
			return rest;
		}
		return isNumber(s) ? numbered(s) : s;
	}

	private static boolean isNumber(String s) {
		return !s.isEmpty() && s.length() <= MAX_GENRE_DIGITS && s.chars().allMatch(c -> c >= '0' && c <= '9');
	}

	private static String numbered(String s) {
		int n = Integer.parseInt(s);
		return n < GENRES.size() ? GENRES.get(n) : "";
	}
}
