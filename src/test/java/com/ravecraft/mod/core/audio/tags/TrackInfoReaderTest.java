package com.ravecraft.mod.core.audio.tags;

import static com.ravecraft.mod.core.audio.tags.TagBytes.concat;
import static com.ravecraft.mod.core.audio.tags.TagBytes.id3Frame;
import static com.ravecraft.mod.core.audio.tags.TagBytes.id3FrameRaw;
import static com.ravecraft.mod.core.audio.tags.TagBytes.id3v1;
import static com.ravecraft.mod.core.audio.tags.TagBytes.id3v2;
import static com.ravecraft.mod.core.audio.tags.TagBytes.id3v2WithSize;
import static com.ravecraft.mod.core.audio.tags.TagBytes.mpegFrame;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
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
		Path[] files = {write("a.mp3", oversized), write("b.mp3", truncated), write("c.mp3")};
		for (Path p : files) {
			TrackInfo[] out = new TrackInfo[1];
			assertDoesNotThrow(() -> out[0] = TrackInfoReader.read(p));
			assertEquals("", out[0].title());
		}
	}

	@Test
	void unknownExtensionIsEmpty() throws IOException {
		assertEquals(TrackInfo.empty("XYZ"), TrackInfoReader.read(write("a.xyz", new byte[8])));
	}
}
