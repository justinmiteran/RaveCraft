package com.ravecraft.mod.core.audio.tags;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Text tags and stream facts of FLAC, Ogg and WAV files. Each reader fills {@code out} with
 * {@code title, artist, album, year, genre} and {@code tech} with {@code {bitrateKbps, sampleRateHz, channels}}.
 * At most {@link TrackInfoReader#BUDGET} bytes are read; seeking past a block costs nothing. Malformed data stops
 * the walk and keeps what was read.
 */
final class ContainerTags {
	private ContainerTags() {
	}

	/** Reads from a file counting the bytes actually read against the budget. */
	private static final class Reader {
		private final RandomAccessFile f;
		private final long length;
		private long used;

		Reader(RandomAccessFile f) throws IOException {
			this.f = f;
			this.length = f.length();
		}

		/** The {@code n} bytes at {@code at}, or null when they lie past the end or past the budget. */
		byte[] read(long at, long n) throws IOException {
			if (at < 0 || n < 0 || at + n > length || used + n > TrackInfoReader.BUDGET) {
				return null;
			}
			byte[] data = new byte[(int) n];
			f.seek(at);
			f.readFully(data);
			used += n;
			return data;
		}
	}

	private static ByteBuffer little(byte[] data) {
		return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
	}

	static void flac(RandomAccessFile file, Map<String, String> out, int[] tech) throws IOException {
		Reader r = new Reader(file);
		byte[] marker = r.read(0, 4);
		if (marker == null || marker[0] != 'f' || marker[1] != 'L' || marker[2] != 'a' || marker[3] != 'C') {
			return;
		}
		long pos = 4;
		while (true) {
			byte[] h = r.read(pos, 4);
			if (h == null) {
				return;
			}
			boolean last = (h[0] & 0x80) != 0;
			int type = h[0] & 0x7F;
			long len = (h[1] & 0xFFL) << 16 | (h[2] & 0xFFL) << 8 | (h[3] & 0xFFL);
			pos += 4;
			if (type == 0 || type == 4) {
				byte[] body = r.read(pos, len);
				if (body == null) {
					return;
				}
				if (type == 0 && len >= 18) {
					long packed = ByteBuffer.wrap(body).getLong(10);
					int rate = (int) (packed >>> 44);
					int channels = (int) ((packed >>> 41) & 7) + 1;
					int bits = (int) ((packed >>> 36) & 31) + 1;
					tech[0] = (int) ((long) rate * bits * channels / 1000);
					tech[1] = rate;
					tech[2] = channels;
				} else if (type == 4) {
					VorbisComments.read(little(body), out);
				}
			}
			if (last || pos + len > r.length) {
				return;
			}
			pos += len;
		}
	}

	/** Returns the codec name: "Ogg Vorbis", "Ogg Opus" or "Ogg". */
	static String ogg(RandomAccessFile file, Map<String, String> out, int[] tech) throws IOException {
		Reader r = new Reader(file);
		String codec = "Ogg";
		ByteArrayOutputStream packet = new ByteArrayOutputStream();
		int index = 0;
		long pos = 0;
		while (index < 2) {
			byte[] h = r.read(pos, 27);
			if (h == null || h[0] != 'O' || h[1] != 'g' || h[2] != 'g' || h[3] != 'S') {
				break;
			}
			int segments = h[26] & 0xFF;
			byte[] lacing = r.read(pos + 27, segments);
			if (lacing == null) {
				break;
			}
			int bodyLength = 0;
			for (byte l : lacing) {
				bodyLength += l & 0xFF;
			}
			byte[] body = r.read(pos + 27 + segments, bodyLength);
			if (body == null) {
				break;
			}
			pos += 27 + segments + bodyLength;
			int at = 0;
			for (byte l : lacing) {
				int n = l & 0xFF;
				packet.write(body, at, n);
				at += n;
				if (n < 255) {
					byte[] done = packet.toByteArray();
					packet.reset();
					if (index == 0) {
						codec = identification(done, tech);
					} else {
						comments(done, out);
					}
					if (++index == 2) {
						break;
					}
				}
			}
		}
		if (index == 1 && packet.size() > 0) {
			// The budget or the file ended inside the comment packet: keep the entries before the cut.
			comments(packet.toByteArray(), out);
		}
		return codec;
	}

	private static boolean startsWith(byte[] data, String prefix) {
		if (data.length < prefix.length()) {
			return false;
		}
		for (int i = 0; i < prefix.length(); i++) {
			if (data[i] != prefix.charAt(i)) {
				return false;
			}
		}
		return true;
	}

	private static String identification(byte[] p, int[] tech) {
		if (p.length >= 28 && p[0] == 1 && startsWith(new byte[] {p[1], p[2], p[3], p[4], p[5], p[6]}, "vorbis")) {
			ByteBuffer b = little(p);
			tech[2] = p[11] & 0xFF;
			tech[1] = b.getInt(12);
			tech[0] = Math.max(0, b.getInt(20)) / 1000;
			return "Ogg Vorbis";
		}
		if (p.length >= 19 && startsWith(p, "OpusHead")) {
			ByteBuffer b = little(p);
			tech[2] = p[9] & 0xFF;
			int rate = b.getInt(12);
			tech[1] = rate == 0 ? 48000 : rate;
			return "Ogg Opus";
		}
		return "Ogg";
	}

	private static void comments(byte[] p, Map<String, String> out) {
		int skip;
		if (p.length > 7 && p[0] == 3 && startsWith(new byte[] {p[1], p[2], p[3], p[4], p[5], p[6]}, "vorbis")) {
			skip = 7;
		} else if (startsWith(p, "OpusTags")) {
			skip = 8;
		} else {
			return;
		}
		ByteBuffer b = little(p);
		b.position(skip);
		VorbisComments.read(b, out);
	}

	static void wav(RandomAccessFile file, Map<String, String> out, int[] tech) throws IOException {
		Reader r = new Reader(file);
		byte[] head = r.read(0, 12);
		if (head == null || head[0] != 'R' || head[1] != 'I' || head[2] != 'F' || head[3] != 'F') {
			return;
		}
		boolean fmtSeen = false;
		boolean listSeen = false;
		long pos = 12;
		while (!(fmtSeen && listSeen)) {
			byte[] h = r.read(pos, 8);
			if (h == null) {
				return;
			}
			String id = new String(h, 0, 4, StandardCharsets.ISO_8859_1);
			long size = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN).getInt(4) & 0xFFFFFFFFL;
			pos += 8;
			if ("fmt ".equals(id) && size >= 16 && !fmtSeen) {
				byte[] body = r.read(pos, 16);
				if (body == null) {
					return;
				}
				ByteBuffer b = little(body);
				tech[2] = b.getShort(2) & 0xFFFF;
				tech[1] = b.getInt(4);
				tech[0] = (int) ((b.getInt(8) & 0xFFFFFFFFL) * 8 / 1000);
				fmtSeen = true;
			} else if ("LIST".equals(id) && size >= 4 && !listSeen) {
				byte[] body = r.read(pos, size);
				if (body == null) {
					return;
				}
				if (startsWith(body, "INFO")) {
					listInfo(body, out);
					listSeen = true;
				}
			}
			long next = pos + size + (size & 1);
			if (next >= r.length) {
				return;
			}
			pos = next;
		}
	}

	private static void listInfo(byte[] body, Map<String, String> out) {
		ByteBuffer b = little(body);
		int at = 4;
		while (at + 8 <= body.length) {
			String id = new String(body, at, 4, StandardCharsets.ISO_8859_1);
			long size = b.getInt(at + 4) & 0xFFFFFFFFL;
			at += 8;
			if (size > body.length - at) {
				return;
			}
			String key = switch (id) {
				case "INAM" -> Id3Tags.TITLE;
				case "IART" -> Id3Tags.ARTIST;
				case "IPRD" -> Id3Tags.ALBUM;
				case "ICRD" -> Id3Tags.YEAR;
				case "IGNR" -> Id3Tags.GENRE;
				default -> null;
			};
			if (key != null && !out.containsKey(key)) {
				String text = riffText(body, at, (int) size);
				if (!text.isEmpty()) {
					out.put(key, Id3Tags.YEAR.equals(key) && text.length() > 4 ? text.substring(0, 4) : text);
				}
			}
			at += (int) size + (int) (size & 1);
		}
	}

	/** Strict UTF-8, falling back to ISO-8859-1; trailing NULs and spaces are dropped. */
	private static String riffText(byte[] b, int at, int size) {
		String s;
		try {
			s = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b, at, size)).toString();
		} catch (CharacterCodingException e) {
			s = new String(b, at, size, StandardCharsets.ISO_8859_1);
		}
		int end = s.length();
		while (end > 0 && (s.charAt(end - 1) == 0 || s.charAt(end - 1) == ' ')) {
			end--;
		}
		return s.substring(0, end);
	}
}
