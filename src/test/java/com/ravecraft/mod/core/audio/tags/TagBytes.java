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
}
