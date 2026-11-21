package com.ravecraft.mod.core.audio.tags;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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

	private Id3Tags() {
	}

	static int syncsafe(ByteBuffer b, int at) {
		return (b.get(at) & 0x7F) << 21 | (b.get(at + 1) & 0x7F) << 14 | (b.get(at + 2) & 0x7F) << 7
				| (b.get(at + 3) & 0x7F);
	}

	/** Moves {@code tag} past the extended header, which starts at its position (flag 0x40 of the tag header). */
	static void skipExtendedHeader(ByteBuffer tag, int version) {
		if (tag.remaining() < 4) {
			return;
		}
		int at = tag.position();
		// v2.3: the size excludes its own 4 bytes; v2.4: it includes them.
		int size = version == 4 ? syncsafe(tag, at) : tag.getInt(at) + 4;
		tag.position(size < 4 || size > tag.remaining() ? tag.limit() : at + size);
	}

	/**
	 * Reads the text frames of an ID3v2.3 or 2.4 tag body from {@code tag}'s position to its limit, setting
	 * {@code title, artist, album, year, genre} in {@code out}. The first value of a key wins. Stops at the first
	 * malformed frame.
	 */
	static void readV2(ByteBuffer tag, int version, Map<String, String> out) {
		if (version != 3 && version != 4) {
			return;
		}
		while (tag.remaining() >= 10) {
			int at = tag.position();
			String id = frameId(tag, at);
			if (id == null) {
				return;
			}
			long size = version == 4 ? syncsafe(tag, at + 4) : tag.getInt(at + 4) & 0xFFFFFFFFL;
			if (size > tag.remaining() - 10) {
				return;
			}
			int body = at + 10;
			String key = switch (id) {
				case "TIT2" -> TITLE;
				case "TPE1" -> ARTIST;
				case "TALB" -> ALBUM;
				case "TDRC", "TYER" -> YEAR;
				case "TCON" -> GENRE;
				default -> null;
			};
			if (key != null && size > 0 && !out.containsKey(key)) {
				String text = text(tag, body, (int) size);
				out.put(key, switch (key) {
					case YEAR -> text.length() > 4 ? text.substring(0, 4) : text;
					case GENRE -> genre(text);
					default -> text;
				});
			}
			tag.position(body + (int) size);
		}
	}

	private static String frameId(ByteBuffer b, int at) {
		char[] id = new char[4];
		for (int i = 0; i < 4; i++) {
			char c = (char) (b.get(at + i) & 0xFF);
			if ((c < 'A' || c > 'Z') && (c < '0' || c > '9')) {
				return null;
			}
			id[i] = c;
		}
		return new String(id);
	}

	/** Decodes a text frame body: an encoding byte, then text up to the first terminator of that encoding. */
	private static String text(ByteBuffer b, int at, int size) {
		int encoding = b.get(at) & 0xFF;
		int from = at + 1;
		int end = at + size;
		boolean wide = encoding == 1 || encoding == 2;
		int step = wide ? 2 : 1;
		int stop = from;
		while (stop + step <= end && (b.get(stop) != 0 || (wide && b.get(stop + 1) != 0))) {
			stop += step;
		}
		byte[] raw = new byte[stop - from];
		b.get(from, raw);
		Charset cs = switch (encoding) {
			case 0 -> StandardCharsets.ISO_8859_1;
			case 1 -> StandardCharsets.UTF_16; // honours the BOM, big-endian without one
			case 2 -> StandardCharsets.UTF_16BE;
			case 3 -> StandardCharsets.UTF_8;
			default -> null;
		};
		if (cs == null) {
			return "";
		}
		return new String(raw, cs).trim();
	}

	/** Fills the keys of {@code out} that are still missing from the 128 last bytes of an MP3, when they hold ID3v1. */
	static void readV1(ByteBuffer last128, Map<String, String> out) {
		if (last128.remaining() < 128) {
			return;
		}
		int at = last128.position();
		if (last128.get(at) != 'T' || last128.get(at + 1) != 'A' || last128.get(at + 2) != 'G') {
			return;
		}
		out.putIfAbsent(TITLE, v1(last128, at + 3, 30));
		out.putIfAbsent(ARTIST, v1(last128, at + 33, 30));
		out.putIfAbsent(ALBUM, v1(last128, at + 63, 30));
		out.putIfAbsent(YEAR, v1(last128, at + 93, 4));
		out.putIfAbsent(GENRE, genre(Integer.toString(last128.get(at + 127) & 0xFF)));
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
		return !s.isEmpty() && s.length() <= 3 && s.chars().allMatch(c -> c >= '0' && c <= '9');
	}

	private static String numbered(String s) {
		int n = Integer.parseInt(s);
		return n < GENRES.size() ? GENRES.get(n) : "";
	}
}
