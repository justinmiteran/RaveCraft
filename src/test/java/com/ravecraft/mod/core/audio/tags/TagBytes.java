package com.ravecraft.mod.core.audio.tags;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Hand-built tag and frame bytes for the reader tests. */
final class TagBytes {
	private TagBytes() {
	}

	static byte[] concat(byte[]... parts) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (byte[] p : parts) {
			out.writeBytes(p);
		}
		return out.toByteArray();
	}

	private static byte[] syncsafe(int n) {
		return new byte[] {(byte) (n >> 21 & 0x7F), (byte) (n >> 14 & 0x7F), (byte) (n >> 7 & 0x7F),
				(byte) (n & 0x7F)};
	}

	private static byte[] plain(int n) {
		return new byte[] {(byte) (n >> 24), (byte) (n >> 16), (byte) (n >> 8), (byte) n};
	}

	/** An ID3v2 tag whose header declares {@code declaredSize} bytes of body. */
	static byte[] id3v2WithSize(int version, int declaredSize, byte[]... frames) {
		return concat("ID3".getBytes(StandardCharsets.US_ASCII), new byte[] {(byte) version, 0, 0},
				syncsafe(declaredSize), concat(frames));
	}

	static byte[] id3v2(int version, byte[]... frames) {
		return id3v2WithSize(version, concat(frames).length, frames);
	}

	/** A frame whose size field covers {@code body}; v3 uses a plain int size, v4 a syncsafe one. */
	static byte[] id3FrameRaw(int version, String id, byte[] body) {
		return concat(id.getBytes(StandardCharsets.US_ASCII),
				version == 4 ? syncsafe(body.length) : plain(body.length), new byte[2], body);
	}

	static byte[] id3Frame(int version, String id, int encoding, byte[] text) {
		return id3FrameRaw(version, id, concat(new byte[] {(byte) encoding}, text));
	}

	/** MPEG-1 Layer III, 128 kb/s, 44.1 kHz, stereo. */
	static byte[] mpegFrame() {
		byte[] f = new byte[417];
		f[0] = (byte) 0xFF;
		f[1] = (byte) 0xFB;
		f[2] = (byte) 0x90;
		return f;
	}

	private static byte[] field(String s, int len) {
		byte[] out = new byte[len];
		byte[] b = s.getBytes(StandardCharsets.ISO_8859_1);
		System.arraycopy(b, 0, out, 0, Math.min(len, b.length));
		return out;
	}

	static byte[] id3v1(String title, String artist, String album, String year, int genre) {
		return concat("TAG".getBytes(StandardCharsets.US_ASCII), field(title, 30), field(artist, 30),
				field(album, 30), field(year, 4), new byte[30], new byte[] {(byte) genre});
	}

	private static byte[] le32(long n) {
		return new byte[] {(byte) n, (byte) (n >> 8), (byte) (n >> 16), (byte) (n >> 24)};
	}

	private static byte[] le16(int n) {
		return new byte[] {(byte) n, (byte) (n >> 8)};
	}

	private static byte[] ascii(String s) {
		return s.getBytes(StandardCharsets.US_ASCII);
	}

	/** A Vorbis comment body (little-endian), without framing bit: vendor, count, then {@code KEY=value} entries. */
	static byte[] vorbisComment(String... kv) {
		byte[] vendor = ascii("test");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.writeBytes(le32(vendor.length));
		out.writeBytes(vendor);
		out.writeBytes(le32(kv.length));
		for (String s : kv) {
			byte[] b = s.getBytes(StandardCharsets.UTF_8);
			out.writeBytes(le32(b.length));
			out.writeBytes(b);
		}
		return out.toByteArray();
	}

	/** A FLAC metadata block with its header (last flag clear). */
	static byte[] flacBlock(int type, byte[] body) {
		return flacBlockDeclaring(type, body.length, body);
	}

	static byte[] flacBlockDeclaring(int type, int declaredLength, byte[] body) {
		return concat(new byte[] {(byte) type, (byte) (declaredLength >> 16), (byte) (declaredLength >> 8),
				(byte) declaredLength}, body);
	}

	/** A FLAC file: marker, STREAMINFO, then the given blocks (built with {@link #flacBlock}); the last is flagged. */
	static byte[] flac(byte[] streamInfo34, byte[]... blocks) {
		byte[][] all = new byte[blocks.length + 1][];
		all[0] = flacBlock(0, streamInfo34);
		System.arraycopy(blocks, 0, all, 1, blocks.length);
		all[all.length - 1][0] |= (byte) 0x80;
		return concat(ascii("fLaC"), concat(all));
	}

	/** An Ogg page with explicit flags, lacing table and body. CRC is left at 0. */
	static byte[] oggRaw(int seq, int flags, int[] lacing, byte[] body) {
		byte[] lace = new byte[lacing.length];
		for (int i = 0; i < lacing.length; i++) {
			lace[i] = (byte) lacing[i];
		}
		return concat(ascii("OggS"), new byte[] {0, (byte) flags}, new byte[8], le32(1), le32(seq), new byte[4],
				new byte[] {(byte) lacing.length}, lace, body);
	}

	/** An Ogg page holding whole packets. */
	static byte[] oggPage(int seq, byte[]... packets) {
		java.util.List<Integer> lacing = new java.util.ArrayList<>();
		for (byte[] p : packets) {
			int n = p.length;
			while (n >= 255) {
				lacing.add(255);
				n -= 255;
			}
			lacing.add(n);
		}
		return oggRaw(seq, 0, lacing.stream().mapToInt(Integer::intValue).toArray(), concat(packets));
	}

	static byte[] chunk(String id, byte[] body) {
		return concat(ascii(id), le32(body.length), body, new byte[body.length & 1]);
	}

	static byte[] chunkDeclaring(String id, long declaredSize, byte[] body) {
		return concat(ascii(id), le32(declaredSize), body);
	}

	static byte[] riff(byte[]... chunks) {
		byte[] body = concat(chunks);
		return concat(ascii("RIFF"), le32(body.length + 4), ascii("WAVE"), body);
	}

	/** A PCM {@code fmt } chunk. */
	static byte[] wavFmt(int channels, int rate, int byteRate) {
		return chunk("fmt ", concat(le16(1), le16(channels), le32(rate), le32(byteRate), le16(channels * 2),
				le16(16)));
	}

	/** A {@code LIST INFO} chunk; entries are {id, text} pairs, each NUL-terminated. */
	static byte[] listInfo(String... idText) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.writeBytes(ascii("INFO"));
		for (int i = 0; i < idText.length; i += 2) {
			out.writeBytes(chunk(idText[i], concat(idText[i + 1].getBytes(StandardCharsets.UTF_8), new byte[1])));
		}
		return chunk("LIST", out.toByteArray());
	}
}
