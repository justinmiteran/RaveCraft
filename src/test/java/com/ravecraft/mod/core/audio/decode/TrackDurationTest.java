package com.ravecraft.mod.core.audio.decode;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrackDurationTest {
	@TempDir
	Path dir;

	private Path write(String name, byte[] data) throws IOException {
		Path p = dir.resolve(name);
		Files.write(p, data);
		return p;
	}

	@Test
	void wavDurationFromHeader() throws IOException {
		int dataSize = 44100 * 4 * 2;
		ByteBuffer b = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
		b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + dataSize);
		b.put("WAVE".getBytes(StandardCharsets.US_ASCII));
		b.put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1).putShort((short) 2)
				.putInt(44100).putInt(44100 * 4).putShort((short) 4).putShort((short) 16);
		b.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(dataSize);
		assertEquals(OptionalLong.of(2000), TrackDuration.of(write("a.WAV", b.array())));
	}

	@Test
	void flacDurationFromStreamInfo() throws IOException {
		ByteBuffer b = ByteBuffer.allocate(42);
		b.put("fLaC".getBytes(StandardCharsets.US_ASCII));
		b.put((byte) 0x80).put((byte) 0).put((byte) 0).put((byte) 34);
		b.putShort((short) 4096).putShort((short) 4096);
		b.put(new byte[6]); // min/max frame size
		long packed = (44100L << 44) | (1L << 41) | (15L << 36) | 88200L; // rate, channels-1, bps-1, samples
		b.putLong(packed);
		b.put(new byte[16]);
		assertEquals(OptionalLong.of(2000), TrackDuration.of(write("a.flac", b.array())));
	}

	@Test
	void mp3XingDuration() throws IOException {
		byte[] data = new byte[1024];
		// MPEG-1 Layer III, no CRC, 128 kbps, 44.1 kHz, stereo
		data[0] = (byte) 0xFF;
		data[1] = (byte) 0xFB;
		data[2] = (byte) 0x90;
		data[3] = 0x00;
		int xing = 4 + 32;
		System.arraycopy("Xing".getBytes(StandardCharsets.US_ASCII), 0, data, xing, 4);
		data[xing + 7] = 1; // frames present
		data[xing + 11] = 77;
		assertEquals(OptionalLong.of(2011), TrackDuration.of(write("a.mp3", data)));
	}

	@Test
	void mp3CbrEstimate() throws IOException {
		byte[] data = new byte[32000];
		data[0] = (byte) 0xFF;
		data[1] = (byte) 0xFB;
		data[2] = (byte) 0x90;
		assertEquals(OptionalLong.of(2000), TrackDuration.of(write("a.mp3", data)));
	}

	@Test
	void mp3SkipsId3v2Tag() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write("ID3".getBytes(StandardCharsets.US_ASCII));
		out.write(new byte[] {4, 0, 0, 0, 0, 0, 10});
		out.write(new byte[10]);
		byte[] frame = new byte[32000];
		frame[0] = (byte) 0xFF;
		frame[1] = (byte) 0xFB;
		frame[2] = (byte) 0x90;
		out.write(frame);
		assertEquals(OptionalLong.of(2000), TrackDuration.of(write("a.mp3", out.toByteArray())));
	}

	private static byte[] oggPage(int type, long granule, byte[] payload) {
		ByteBuffer b = ByteBuffer.allocate(27 + 1 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
		b.put("OggS".getBytes(StandardCharsets.US_ASCII)).put((byte) 0).put((byte) type).putLong(granule);
		b.putInt(1).putInt(0).putInt(0).put((byte) 1).put((byte) payload.length).put(payload);
		return b.array();
	}

	@Test
	void oggLastGranule() throws IOException {
		ByteBuffer id = ByteBuffer.allocate(30).order(ByteOrder.LITTLE_ENDIAN);
		id.put((byte) 1).put("vorbis".getBytes(StandardCharsets.US_ASCII)).putInt(0).put((byte) 2).putInt(48000);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(oggPage(2, 0, id.array()));
		out.write(oggPage(4, 96000, new byte[10]));
		assertEquals(OptionalLong.of(2000), TrackDuration.of(write("a.ogg", out.toByteArray())));
	}

	@Test
	void truncatedFileIsUnknown() throws IOException {
		assertEquals(OptionalLong.empty(), TrackDuration.of(write("a.wav", new byte[] {'R', 'I', 'F'})));
		assertEquals(OptionalLong.empty(), TrackDuration.of(write("a.flac", new byte[] {'f', 'L'})));
		assertEquals(OptionalLong.empty(), TrackDuration.of(write("a.mp3", new byte[3])));
		assertEquals(OptionalLong.empty(), TrackDuration.of(write("a.ogg", new byte[20])));
		assertEquals(OptionalLong.empty(), TrackDuration.of(dir.resolve("missing.wav")));
	}

	@Test
	void unknownExtensionIsUnknown() throws IOException {
		assertEquals(OptionalLong.empty(), TrackDuration.of(write("a.txt", new byte[100])));
	}
}
