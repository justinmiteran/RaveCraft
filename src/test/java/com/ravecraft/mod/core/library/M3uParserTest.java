package com.ravecraft.mod.core.library;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class M3uParserTest {
	private static final String BACKSLASH = String.valueOf((char) 92);

	@TempDir
	Path dir;

	@Test
	void skipsCommentsAndBlankLinesAndResolvesRelativePaths() {
		Path absolute = dir.resolve("abs").resolve("b.flac").toAbsolutePath();
		List<String> lines = List.of("#EXTM3U", "#EXTINF:123,Artist - Title", "", "   ",
				"  music" + BACKSLASH + "a.mp3  ", absolute.toString());
		List<Path> paths = M3uParser.parse(lines, dir);
		assertEquals(List.of(dir.resolve("music").resolve("a.mp3").normalize(), absolute), paths);
	}

	@Test
	void acceptsFileUris() {
		Path track = dir.resolve("c.ogg").toAbsolutePath();
		assertEquals(List.of(track), M3uParser.parse(List.of(track.toUri().toString()), dir));
	}

	@Test
	void readsLatin1Playlists() throws IOException {
		Path playlist = dir.resolve("old.m3u");
		Files.writeString(playlist, "chanson_\u00e9.mp3\n", StandardCharsets.ISO_8859_1);
		List<Path> paths = M3uParser.read(playlist);
		assertEquals(1, paths.size());
		assertEquals("chanson_\u00e9.mp3", paths.getFirst().getFileName().toString());
	}

	@Test
	void readsUtf8PlaylistsWithBom() throws IOException {
		Path playlist = dir.resolve("new.m3u8");
		Files.writeString(playlist, "\uFEFFpiste_\u00fc.flac\n", StandardCharsets.UTF_8);
		List<Path> paths = M3uParser.read(playlist);
		assertEquals(List.of(dir.resolve("piste_\u00fc.flac").normalize()), paths);
	}
}
