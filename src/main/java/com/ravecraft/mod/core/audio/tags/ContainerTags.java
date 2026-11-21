package com.ravecraft.mod.core.audio.tags;

import com.ravecraft.mod.core.audio.Bytes;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Map;

/**
 * Text tags and stream facts of FLAC, Ogg and WAV files. Each reader fills {@code out} with
 * {@code title, artist, album, year, genre} and {@code tech} with {@code {bitrateKbps, sampleRateHz, channels}}.
 * At most {@link TrackInfoReader#BUDGET} bytes are read; seeking past a block costs nothing. Malformed data stops
 * the walk and keeps what was read.
 */
final class ContainerTags {
	private static final int TECH_KBPS = 0;
	private static final int TECH_RATE = 1;
	private static final int TECH_CHANNELS = 2;
	private static final int BITS_PER_BYTE = 8;
	private static final int BITS_PER_KILOBIT = 1000;
	private static final int YEAR_LENGTH = 4;

	// FLAC: "fLaC", then metadata blocks of a 4-byte header (last flag, type, 24-bit length) and a body.
	private static final int FLAC_BLOCK_HEADER = 4;
	private static final int FLAC_LAST_FLAG = 0x80;
	private static final int FLAC_TYPE_MASK = 0x7F;
	private static final long FLAC_LENGTH_MASK = 0xFFFFFF;
	private static final int FLAC_STREAMINFO = 0;
	private static final int FLAC_VORBIS_COMMENT = 4;
	private static final int FLAC_STREAMINFO_MIN = 18;
	private static final int FLAC_PACKED_AT = 10;
	private static final int FLAC_RATE_SHIFT = 44;
	private static final int FLAC_CHANNELS_SHIFT = 41;
	private static final int FLAC_CHANNELS_MASK = 7;
	private static final int FLAC_BITS_SHIFT = 36;
	private static final int FLAC_BITS_MASK = 31;

	// Ogg pages: a 27-byte header ending with the segment count, the lacing values, then the body.
	private static final int OGG_PAGE_HEADER = 27;
	private static final int OGG_SEGMENTS_AT = 26;
	private static final int OGG_FULL_SEGMENT = 255;

	// Vorbis and Opus identification and comment packets.
	private static final int VORBIS_ID_PACKET = 1;
	private static final int VORBIS_COMMENT_PACKET = 3;
	private static final int VORBIS_MAGIC_AT = 1;
	private static final int VORBIS_PACKET_HEADER = 7;
	private static final int VORBIS_ID_MIN = 28;
	private static final int VORBIS_CHANNELS_AT = 11;
	private static final int VORBIS_RATE_AT = 12;
	private static final int VORBIS_NOMINAL_BITRATE_AT = 20;
	private static final int OPUS_HEAD_MIN = 19;
	private static final int OPUS_CHANNELS_AT = 9;
	private static final int OPUS_RATE_AT = 12;
	private static final int OPUS_DEFAULT_RATE = 48000;

	// RIFF/WAVE: "RIFF" size "WAVE", then chunks of id (4) + size (4) + body, padded to even length.
	private static final int RIFF_HEADER = 12;
	private static final int CHUNK_HEADER = 8;
	private static final int CHUNK_SIZE_AT = 4;
	private static final int FMT_LENGTH = 16;
	private static final int FMT_CHANNELS_AT = 2;
	private static final int FMT_RATE_AT = 4;
	private static final int FMT_BYTE_RATE_AT = 8;

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
			boolean inFile = at >= 0 && n >= 0 && at + n <= length;
			if (!inFile || used + n > TrackInfoReader.BUDGET) {
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

	/** Whether {@code data} holds the ASCII text {@code magic} at {@code at}. */
	private static boolean hasAt(byte[] data, int at, String magic) {
		if (data.length < at + magic.length()) {
			return false;
		}
		for (int i = 0; i < magic.length(); i++) {
			if (data[at + i] != magic.charAt(i)) {
				return false;
			}
		}
		return true;
	}

	private static boolean startsWith(byte[] data, String prefix) {
		return hasAt(data, 0, prefix);
	}

	/**
	 * Reads a FLAC file: STREAMINFO for the stream facts and VORBIS_COMMENT for the tags.
	 *
	 * @param file the FLAC file
	 * @param out the tags read so far
	 * @param tech the stream facts, filled in place
	 * @throws IOException when the file cannot be read
	 */
	static void flac(RandomAccessFile file, Map<String, String> out, int[] tech) throws IOException {
		Reader r = new Reader(file);
		byte[] marker = r.read(0, Bytes.FOUR_CC);
		if (marker == null || !startsWith(marker, "fLaC")) {
			return;
		}
		long pos = Bytes.FOUR_CC;
		while (true) {
			byte[] h = r.read(pos, FLAC_BLOCK_HEADER);
			if (h == null) {
				return;
			}
			int type = h[0] & FLAC_TYPE_MASK;
			long len = Integer.toUnsignedLong(ByteBuffer.wrap(h).getInt()) & FLAC_LENGTH_MASK;
			pos += FLAC_BLOCK_HEADER;
			if (!flacBlock(r, pos, type, len, out, tech)) {
				return;
			}
			boolean last = (h[0] & FLAC_LAST_FLAG) != 0;
			if (last || pos + len > r.length) {
				return;
			}
			pos += len;
		}
	}

	/** Reads one metadata block body; false when it lies past the end or the budget. */
	private static boolean flacBlock(Reader r, long pos, int type, long len, Map<String, String> out, int[] tech)
			throws IOException {
		if (type != FLAC_STREAMINFO && type != FLAC_VORBIS_COMMENT) {
			return true;
		}
		byte[] body = r.read(pos, len);
		if (body == null) {
			return false;
		}
		if (type == FLAC_VORBIS_COMMENT) {
			VorbisComments.read(little(body), out);
		} else if (len >= FLAC_STREAMINFO_MIN) {
			streamInfo(ByteBuffer.wrap(body).getLong(FLAC_PACKED_AT), tech);
		}
		return true;
	}

	private static void streamInfo(long packed, int[] tech) {
		int rate = (int) (packed >>> FLAC_RATE_SHIFT);
		int channels = (int) (packed >>> FLAC_CHANNELS_SHIFT & FLAC_CHANNELS_MASK) + 1;
		int bits = (int) (packed >>> FLAC_BITS_SHIFT & FLAC_BITS_MASK) + 1;
		tech[TECH_KBPS] = (int) ((long) rate * bits * channels / BITS_PER_KILOBIT);
		tech[TECH_RATE] = rate;
		tech[TECH_CHANNELS] = channels;
	}

	/** Splits Ogg pages into packets, reading a page only when no complete packet is waiting. */
	private static final class OggPackets {
		private final Reader r;
		private final ArrayDeque<byte[]> ready = new ArrayDeque<>();
		private final ByteArrayOutputStream partial = new ByteArrayOutputStream();
		private long pos;

		OggPackets(Reader r) {
			this.r = r;
		}

		/** The next complete packet, or null when the pages end, break or exceed the budget. */
		byte[] next() throws IOException {
			while (ready.isEmpty()) {
				if (!readPage()) {
					return null;
				}
			}
			return ready.poll();
		}

		/** The bytes of the packet still incomplete when the pages ended. */
		byte[] partial() {
			return partial.toByteArray();
		}

		/** Reads the next page and splits it; false when it is missing, broken or past the budget. */
		private boolean readPage() throws IOException {
			byte[] h = r.read(pos, OGG_PAGE_HEADER);
			if (h == null || !startsWith(h, "OggS")) {
				return false;
			}
			int segments = Byte.toUnsignedInt(h[OGG_SEGMENTS_AT]);
			byte[] lacing = r.read(pos + OGG_PAGE_HEADER, segments);
			if (lacing == null) {
				return false;
			}
			int bodyLength = 0;
			for (byte l : lacing) {
				bodyLength += Byte.toUnsignedInt(l);
			}
			byte[] body = r.read(pos + OGG_PAGE_HEADER + segments, bodyLength);
			if (body == null) {
				return false;
			}
			pos += OGG_PAGE_HEADER + segments + bodyLength;
			split(lacing, body);
			return true;
		}

		private void split(byte[] lacing, byte[] body) {
			int at = 0;
			for (byte l : lacing) {
				int n = Byte.toUnsignedInt(l);
				partial.write(body, at, n);
				at += n;
				if (n < OGG_FULL_SEGMENT) {
					ready.add(partial.toByteArray());
					partial.reset();
				}
			}
		}
	}

	/**
	 * Reads an Ogg file's first two packets: the codec identification, then its comments.
	 *
	 * @param file the Ogg file
	 * @param out the tags read so far
	 * @param tech the stream facts, filled in place
	 * @return the codec name: "Ogg Vorbis", "Ogg Opus" or "Ogg"
	 * @throws IOException when the file cannot be read
	 */
	static String ogg(RandomAccessFile file, Map<String, String> out, int[] tech) throws IOException {
		OggPackets packets = new OggPackets(new Reader(file));
		byte[] identification = packets.next();
		if (identification == null) {
			return "Ogg";
		}
		String codec = identification(identification, tech);
		byte[] tags = packets.next();
		// When the budget or the file ends inside the comment packet, keep the entries before the cut.
		comments(tags != null ? tags : packets.partial(), out);
		return codec;
	}

	/** Fills {@code tech} from a Vorbis or Opus identification packet and returns the codec name. */
	private static String identification(byte[] p, int[] tech) {
		if (p.length >= VORBIS_ID_MIN && p[0] == VORBIS_ID_PACKET && hasAt(p, VORBIS_MAGIC_AT, "vorbis")) {
			ByteBuffer b = little(p);
			tech[TECH_CHANNELS] = Byte.toUnsignedInt(p[VORBIS_CHANNELS_AT]);
			tech[TECH_RATE] = b.getInt(VORBIS_RATE_AT);
			tech[TECH_KBPS] = Math.max(0, b.getInt(VORBIS_NOMINAL_BITRATE_AT)) / BITS_PER_KILOBIT;
			return "Ogg Vorbis";
		}
		if (p.length >= OPUS_HEAD_MIN && startsWith(p, "OpusHead")) {
			tech[TECH_CHANNELS] = Byte.toUnsignedInt(p[OPUS_CHANNELS_AT]);
			int rate = little(p).getInt(OPUS_RATE_AT);
			tech[TECH_RATE] = rate == 0 ? OPUS_DEFAULT_RATE : rate;
			return "Ogg Opus";
		}
		return "Ogg";
	}

	/** Reads the tags of a Vorbis or Opus comment packet; other packets are ignored. */
	private static void comments(byte[] p, Map<String, String> out) {
		int skip;
		if (p.length > VORBIS_PACKET_HEADER && p[0] == VORBIS_COMMENT_PACKET && hasAt(p, VORBIS_MAGIC_AT, "vorbis")) {
			skip = VORBIS_PACKET_HEADER;
		} else if (startsWith(p, "OpusTags")) {
			skip = "OpusTags".length();
		} else {
			return;
		}
		ByteBuffer b = little(p);
		b.position(skip);
		VorbisComments.read(b, out);
	}

	/**
	 * Reads a WAV file: the fmt chunk for the stream facts and the LIST/INFO chunk for the tags.
	 *
	 * @param file the WAV file
	 * @param out the tags read so far
	 * @param tech the stream facts, filled in place
	 * @throws IOException when the file cannot be read
	 */
	static void wav(RandomAccessFile file, Map<String, String> out, int[] tech) throws IOException {
		Reader r = new Reader(file);
		byte[] head = r.read(0, RIFF_HEADER);
		if (head == null || !startsWith(head, "RIFF")) {
			return;
		}
		WavWalk walk = new WavWalk(r, out, tech);
		long pos = RIFF_HEADER;
		while (!walk.done()) {
			byte[] h = r.read(pos, CHUNK_HEADER);
			if (h == null) {
				return;
			}
			String id = new String(h, 0, Bytes.FOUR_CC, StandardCharsets.ISO_8859_1);
			long size = Bytes.u32(little(h), CHUNK_SIZE_AT);
			pos += CHUNK_HEADER;
			long next = pos + size + (size & 1);
			if (!walk.chunk(pos, id, size) || next >= r.length) {
				return;
			}
			pos = next;
		}
	}

	/** The WAV chunks read so far: the first fmt and the first LIST/INFO, then the walk stops. */
	private static final class WavWalk {
		private final Reader r;
		private final Map<String, String> out;
		private final int[] tech;
		private boolean fmtSeen;
		private boolean listSeen;

		WavWalk(Reader r, Map<String, String> out, int[] tech) {
			this.r = r;
			this.out = out;
			this.tech = tech;
		}

		boolean done() {
			return fmtSeen && listSeen;
		}

		/** Reads one chunk body when it is wanted; false when it lies past the end or the budget. */
		boolean chunk(long pos, String id, long size) throws IOException {
			if ("fmt ".equals(id) && size >= FMT_LENGTH && !fmtSeen) {
				return fmtChunk(pos);
			}
			if ("LIST".equals(id) && size >= Bytes.FOUR_CC && !listSeen) {
				return listChunk(pos, size);
			}
			return true;
		}

		private boolean fmtChunk(long pos) throws IOException {
			byte[] body = r.read(pos, FMT_LENGTH);
			if (body == null) {
				return false;
			}
			fmt(little(body));
			fmtSeen = true;
			return true;
		}

		private boolean listChunk(long pos, long size) throws IOException {
			byte[] body = r.read(pos, size);
			if (body == null) {
				return false;
			}
			if (startsWith(body, "INFO")) {
				listInfo(body, out);
				listSeen = true;
			}
			return true;
		}

		private void fmt(ByteBuffer b) {
			tech[TECH_CHANNELS] = Bytes.u16(b, FMT_CHANNELS_AT);
			tech[TECH_RATE] = b.getInt(FMT_RATE_AT);
			tech[TECH_KBPS] = (int) (Bytes.u32(b, FMT_BYTE_RATE_AT) * BITS_PER_BYTE / BITS_PER_KILOBIT);
		}
	}

	/** Reads the INFO sub-chunks of a LIST chunk body that starts with "INFO". */
	private static void listInfo(byte[] body, Map<String, String> out) {
		ByteBuffer b = little(body);
		int at = Bytes.FOUR_CC;
		while (at + CHUNK_HEADER <= body.length) {
			String id = new String(body, at, Bytes.FOUR_CC, StandardCharsets.ISO_8859_1);
			long size = Bytes.u32(b, at + CHUNK_SIZE_AT);
			at += CHUNK_HEADER;
			if (size > body.length - at) {
				return;
			}
			String key = infoKey(id);
			if (key != null && !out.containsKey(key)) {
				String text = riffText(body, at, (int) size);
				if (!text.isEmpty()) {
					out.put(key, Id3Tags.YEAR.equals(key) && text.length() > YEAR_LENGTH
							? text.substring(0, YEAR_LENGTH) : text);
				}
			}
			at += (int) size + (int) (size & 1);
		}
	}

	private static String infoKey(String id) {
		return switch (id) {
			case "INAM" -> Id3Tags.TITLE;
			case "IART" -> Id3Tags.ARTIST;
			case "IPRD" -> Id3Tags.ALBUM;
			case "ICRD" -> Id3Tags.YEAR;
			case "IGNR" -> Id3Tags.GENRE;
			default -> null;
		};
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
