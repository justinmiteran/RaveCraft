package com.ravecraft.mod.core.audio.tags;

import static com.ravecraft.mod.core.audio.tags.TagBytes.chunk;
import static com.ravecraft.mod.core.audio.tags.TagBytes.chunkDeclaring;
import static com.ravecraft.mod.core.audio.tags.TagBytes.concat;
import static com.ravecraft.mod.core.audio.tags.TagBytes.flac;
import static com.ravecraft.mod.core.audio.tags.TagBytes.flacBlock;
import static com.ravecraft.mod.core.audio.tags.TagBytes.flacBlockDeclaring;
import static com.ravecraft.mod.core.audio.tags.TagBytes.id3Frame;
import static com.ravecraft.mod.core.audio.tags.TagBytes.id3FrameRaw;
import static com.ravecraft.mod.core.audio.tags.TagBytes.id3v1;
import static com.ravecraft.mod.core.audio.tags.TagBytes.id3v2;
import static com.ravecraft.mod.core.audio.tags.TagBytes.id3v2WithSize;
import static com.ravecraft.mod.core.audio.tags.TagBytes.listInfo;
import static com.ravecraft.mod.core.audio.tags.TagBytes.mpegFrame;
import static com.ravecraft.mod.core.audio.tags.TagBytes.oggPage;
import static com.ravecraft.mod.core.audio.tags.TagBytes.oggRaw;
import static com.ravecraft.mod.core.audio.tags.TagBytes.riff;
import static com.ravecraft.mod.core.audio.tags.TagBytes.vorbisComment;
import static com.ravecraft.mod.core.audio.tags.TagBytes.wavFmt;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrackInfoReaderTest {
	private static final int MIB = 1 << 20;

	@TempDir
	Path dir;

	private Path write(String name, byte[]... parts) throws IOException {
		Path p = dir.resolve(name);
		Files.write(p, concat(parts));
		return p;
	}

	private static byte[] utf8(String s) {
		return s.getBytes(StandardCharsets.UTF_8);
	}

	private static byte[] latin1(String s) {
		return s.getBytes(StandardCharsets.ISO_8859_1);
	}

	@Test
	void id3v24Utf8Tags() throws IOException {
		byte[] tag = id3v2(4, id3Frame(4, "TIT2", 3, utf8("Night Drive")), id3Frame(4, "TPE1", 3, utf8("Alex")),
				id3Frame(4, "TALB", 3, utf8("Neon")), id3Frame(4, "TDRC", 3, utf8("2024-05-01")),
				id3Frame(4, "TCON", 3, utf8("Synthwave")));
		TrackInfo info = TrackInfoReader.read(write("a.mp3", tag, mpegFrame()));
		assertEquals("Night Drive", info.title());
		assertEquals("Alex", info.artist());
		assertEquals("Neon", info.album());
		assertEquals("2024", info.year());
		assertEquals("Synthwave", info.genre());
		assertEquals("MP3", info.format());
		assertEquals(128, info.bitrateKbps());
		assertEquals(44100, info.sampleRateHz());
		assertEquals(2, info.channels());
	}

	@Test
	void id3v23Utf16Bom() throws IOException {
		byte[] title = concat(new byte[] {(byte) 0xFF, (byte) 0xFE},
				"Été".getBytes(StandardCharsets.UTF_16LE));
		byte[] tag = id3v2(3, id3Frame(3, "TIT2", 1, title), id3Frame(3, "TYER", 0, latin1("1999")));
		TrackInfo info = TrackInfoReader.read(write("a.mp3", tag, mpegFrame()));
		assertEquals("Été", info.title());
		assertEquals("1999", info.year());
	}

	@Test
	void id3Utf16BeAndLatin1() throws IOException {
		byte[] tag = id3v2(4, id3Frame(4, "TPE1", 2, "Zoé".getBytes(StandardCharsets.UTF_16BE)),
				id3Frame(4, "TALB", 0, latin1("Café")),
				id3Frame(4, "TIT2", 3, concat(utf8("Trim"), new byte[] {0, 0, 0})));
		TrackInfo info = TrackInfoReader.read(write("a.mp3", tag, mpegFrame()));
		assertEquals("Zoé", info.artist());
		assertEquals("Café", info.album());
		assertEquals("Trim", info.title());
	}

	@Test
	void id3NumericGenre() throws IOException {
		assertEquals("Rock", genreOf("(17)"));
		assertEquals("Rock", genreOf("17"));
		assertEquals("", genreOf("(200)"));
	}

	private String genreOf(String raw) throws IOException {
		byte[] tag = id3v2(4, id3Frame(4, "TCON", 3, utf8(raw)));
		return TrackInfoReader.read(write("g.mp3", tag, mpegFrame())).genre();
	}

	@Test
	void id3v1Fallback() throws IOException {
		TrackInfo info = TrackInfoReader.read(write("a.mp3", mpegFrame(), id3v1("Old", "Band", "LP", "1987", 13)));
		assertEquals("Old", info.title());
		assertEquals("Band", info.artist());
		assertEquals("LP", info.album());
		assertEquals("1987", info.year());
		assertEquals("Pop", info.genre());
	}

	@Test
	void id3v1OnlyFillsMissingFields() throws IOException {
		byte[] tag = id3v2(4, id3Frame(4, "TIT2", 3, utf8("New")));
		TrackInfo info = TrackInfoReader
				.read(write("a.mp3", tag, mpegFrame(), id3v1("Old", "Band", "LP", "1987", 13)));
		assertEquals("New", info.title());
		assertEquals("Band", info.artist());
	}

	@Test
	void hugeId3TagStillReadsTitle() throws IOException {
		byte[] apic = id3FrameRaw(4, "APIC", new byte[2 * MIB]);
		byte[] tag = id3v2WithSize(4, 5 * MIB, id3Frame(4, "TIT2", 3, utf8("Big")),
				id3Frame(4, "TPE1", 3, utf8("Tag")), apic);
		TrackInfo info = TrackInfoReader.read(write("a.mp3", tag));
		assertEquals("Big", info.title());
		assertEquals("Tag", info.artist());
	}

	@Test
	void textCutTo128() throws IOException {
		byte[] tag = id3v2(4, id3Frame(4, "TIT2", 3, utf8("x".repeat(300))));
		assertEquals(128, TrackInfoReader.read(write("a.mp3", tag, mpegFrame())).title().length());
	}

	@Test
	void sharedTitleNeedsBoth() throws IOException {
		byte[] both = id3v2(4, id3Frame(4, "TIT2", 3, utf8("Night Drive")), id3Frame(4, "TPE1", 3, utf8("Alex")));
		assertEquals(Optional.of("Alex — Night Drive"),
				TrackInfoReader.read(write("a.mp3", both, mpegFrame())).sharedTitle());
		byte[] titleOnly = id3v2(4, id3Frame(4, "TIT2", 3, utf8("Night Drive")));
		assertEquals(Optional.empty(), TrackInfoReader.read(write("b.mp3", titleOnly, mpegFrame())).sharedTitle());
		byte[] noise = new byte[4096];
		new Random(7).nextBytes(noise);
		TrackInfo junk = TrackInfoReader.read(write("x.mp3", noise));
		assertEquals("", junk.title());
		assertEquals("", junk.artist());
		assertEquals(Optional.empty(), junk.sharedTitle());
	}

	@Test
	void brokenFilesNeverThrow() throws IOException {
		byte[] oversized = concat(id3v2WithSize(4, 20, id3FrameRaw(4, "TIT2", new byte[100])), mpegFrame());
		byte[] truncated = concat("ID3".getBytes(StandardCharsets.US_ASCII), new byte[] {4, 0, 0});
		byte[] flacOverrun = flac(streamInfo(), flacBlockDeclaring(4, 0xFFFFFF, new byte[10]));
		byte[] hugeCount = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).putInt(0).putInt(0x7FFFFFFF).array();
		byte[] flacCount = flac(streamInfo(), flacBlock(4, hugeCount));
		byte[] oggOverrun = concat(ascii("OggS"), new byte[22], new byte[] {(byte) 255}, new byte[10]);
		byte[] wavOverrun = riff(wavFmt(2, 44100, 176400), chunkDeclaring("LIST", 0xFFFFFFF0L, new byte[8]));
		Path[] files = {write("a.mp3", oversized), write("b.mp3", truncated), write("c.mp3"),
				write("d.flac", flacOverrun), write("e.flac", flacCount), write("f.ogg", oggOverrun),
				write("g.wav", wavOverrun), write("h.ogg", noise(2048)), write("i.flac", noise(2048)),
				write("j.wav", noise(2048))};
		for (Path p : files) {
			TrackInfo[] out = new TrackInfo[1];
			assertDoesNotThrow(() -> out[0] = TrackInfoReader.read(p));
			assertEquals("", out[0].title());
		}
	}

	private static byte[] noise(int n) {
		byte[] b = new byte[n];
		new Random(11).nextBytes(b);
		return b;
	}

	private static byte[] ascii(String s) {
		return s.getBytes(StandardCharsets.US_ASCII);
	}

	private static byte[] streamInfo() {
		ByteBuffer b = ByteBuffer.allocate(34);
		b.putShort((short) 4096).putShort((short) 4096);
		b.put(new byte[6]);
		b.putLong((44100L << 44) | (1L << 41) | (15L << 36) | 88200L);
		return b.array();
	}

	private static ByteBuffer le(int n) {
		return ByteBuffer.allocate(n).order(ByteOrder.LITTLE_ENDIAN);
	}

	private static byte[] vorbisId() {
		return concat(new byte[] {1}, ascii("vorbis"),
				le(23).putInt(0).put((byte) 2).putInt(44100).putInt(0).putInt(192000).putInt(0).put((byte) 0xB8)
						.array());
	}

	private static byte[] vorbisComments(String... kv) {
		return concat(new byte[] {3}, ascii("vorbis"), vorbisComment(kv), new byte[] {1});
	}

	@Test
	void flacVorbisComment() throws IOException {
		byte[] data = flac(streamInfo(), flacBlock(6, new byte[300]),
				flacBlock(4, vorbisComment("title=Rain", "ARTIST=Mia", "DATE=2021-03-03")));
		TrackInfo info = TrackInfoReader.read(write("a.flac", data));
		assertEquals("Rain", info.title());
		assertEquals("Mia", info.artist());
		assertEquals("2021", info.year());
		assertEquals("FLAC", info.format());
		assertEquals(44100, info.sampleRateHz());
		assertEquals(2, info.channels());
		assertEquals(1411, info.bitrateKbps());
		assertEquals(2000, info.durationMs());
	}

	@Test
	void flacFirstValueWins() throws IOException {
		byte[] data = flac(streamInfo(), flacBlock(4, vorbisComment("TITLE=One", "title=Two")));
		assertEquals("One", TrackInfoReader.read(write("a.flac", data)).title());
	}

	@Test
	void oggVorbisComments() throws IOException {
		byte[] data = concat(oggPage(0, vorbisId()), oggPage(1, vorbisComments("TITLE=Wave", "ARTIST=Kai")));
		TrackInfo info = TrackInfoReader.read(write("a.ogg", data));
		assertEquals("Wave", info.title());
		assertEquals("Kai", info.artist());
		assertEquals("Ogg Vorbis", info.format());
		assertEquals(192, info.bitrateKbps());
		assertEquals(44100, info.sampleRateHz());
		assertEquals(2, info.channels());
	}

	@Test
	void oggCommentSpanningPages() throws IOException {
		byte[] packet = vorbisComments("TITLE=Long", "COMMENT=" + "z".repeat(300));
		byte[] first = java.util.Arrays.copyOfRange(packet, 0, 255);
		byte[] rest = java.util.Arrays.copyOfRange(packet, 255, packet.length);
		byte[] data = concat(oggPage(0, vorbisId()), oggRaw(1, 0, new int[] {255}, first),
				oggRaw(2, 1, new int[] {rest.length}, rest));
		assertEquals("Long", TrackInfoReader.read(write("a.ogg", data)).title());
	}

	@Test
	void oggHugeCommentStillReadsTitle() throws IOException {
		byte[] packet = vorbisComments("TITLE=Big", "ARTIST=Cover", "METADATA_BLOCK_PICTURE=" + "A".repeat(3 * MIB / 2));
		java.io.ByteArrayOutputStream pages = new java.io.ByteArrayOutputStream();
		pages.writeBytes(oggPage(0, vorbisId()));
		int per = 255 * 255;
		int seq = 1;
		for (int at = 0; at < packet.length; at += per, seq++) {
			byte[] part = java.util.Arrays.copyOfRange(packet, at, Math.min(packet.length, at + per));
			boolean full = part.length == per;
			int[] lacing = new int[full ? 255 : part.length / 255 + 1];
			java.util.Arrays.fill(lacing, 255);
			if (!full) {
				lacing[lacing.length - 1] = part.length % 255;
			}
			pages.writeBytes(oggRaw(seq, at == 0 ? 0 : 1, lacing, part));
		}
		TrackInfo info = TrackInfoReader.read(write("a.ogg", pages.toByteArray()));
		assertEquals("Big", info.title());
		assertEquals("Cover", info.artist());
	}

	@Test
	void oggOpusTags()throws IOException {
		byte[] head = concat(ascii("OpusHead"), le(11).put((byte) 1).put((byte) 2).putShort((short) 312)
				.putInt(48000).putShort((short) 0).put((byte) 0).array());
		byte[] tags = concat(ascii("OpusTags"), vorbisComment("TITLE=Op"));
		TrackInfo info = TrackInfoReader.read(write("a.opus", oggPage(0, head), oggPage(1, tags)));
		assertEquals("Op", info.title());
		assertEquals("Ogg Opus", info.format());
		assertEquals(48000, info.sampleRateHz());
		assertEquals(2, info.channels());
		assertEquals(0, info.bitrateKbps());
	}

	@Test
	void wavListInfo() throws IOException {
		byte[] data = riff(wavFmt(2, 44100, 176400),
				listInfo("INAM", "Loop", "IART", "Duo", "IPRD", "EP", "ICRD", "2020", "IGNR", "House"),
				chunk("data", new byte[4]));
		TrackInfo info = TrackInfoReader.read(write("a.wav", data));
		assertEquals("Loop", info.title());
		assertEquals("Duo", info.artist());
		assertEquals("EP", info.album());
		assertEquals("2020", info.year());
		assertEquals("House", info.genre());
		assertEquals("WAV", info.format());
		assertEquals(1411, info.bitrateKbps());
		assertEquals(44100, info.sampleRateHz());
		assertEquals(2, info.channels());
	}

	@Test
	void wavTrailingListAfterData() throws IOException {
		byte[] data = riff(wavFmt(2, 44100, 176400), chunk("data", new byte[64]), listInfo("INAM", "Late"));
		assertEquals("Late", TrackInfoReader.read(write("a.wav", data)).title());
	}

	@Test
	void unknownExtensionIsEmpty() throws IOException {
		assertEquals(TrackInfo.empty("XYZ"), TrackInfoReader.read(write("a.xyz", new byte[8])));
	}
}
