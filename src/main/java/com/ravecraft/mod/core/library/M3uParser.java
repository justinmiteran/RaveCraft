package com.ravecraft.mod.core.library;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads {@code .m3u} / {@code .m3u8} playlists. Comment lines ({@code #EXTM3U}, {@code #EXTINF}...) and blank lines
 * are skipped; relative entries are resolved against the playlist's folder. Entries are not checked for existence.
 */
public final class M3uParser {
	private static final char BOM = '﻿';

	private M3uParser() {
	}

	public static List<Path> read(Path playlist) throws IOException {
		byte[] bytes = Files.readAllBytes(playlist);
		String text = decode(bytes, AudioFiles.extension(playlist).equals("m3u8"));
		Path base = playlist.toAbsolutePath().getParent();
		return parse(text.lines().toList(), base);
	}

	public static List<Path> parse(List<String> lines, Path baseDir) {
		List<Path> paths = new ArrayList<>();
		for (String raw : lines) {
			String line = raw.strip();
			if (!line.isEmpty() && line.charAt(0) == BOM) {
				line = line.substring(1).strip();
			}
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			Path path = toPath(line, baseDir);
			if (path != null) {
				paths.add(path);
			}
		}
		return paths;
	}

	private static Path toPath(String entry, Path baseDir) {
		try {
			if (entry.regionMatches(true, 0, "file:", 0, 5)) {
				return Path.of(URI.create(entry));
			}
			// Playlists written on Windows use '\' even when read elsewhere, and vice versa.
			String normalised = entry.replace('\\', '/');
			Path path = Path.of(normalised);
			if (path.isAbsolute() || isWindowsAbsolute(entry)) {
				return Path.of(entry);
			}
			return baseDir.resolve(path).normalize();
		} catch (IllegalArgumentException e) { // includes InvalidPathException
			return null;
		}
	}

	private static boolean isWindowsAbsolute(String entry) {
		return entry.length() > 2 && Character.isLetter(entry.charAt(0)) && entry.charAt(1) == ':';
	}

	/** {@code .m3u8} is UTF-8; {@code .m3u} is tried as UTF-8 and falls back to Latin-1 (Windows players). */
	private static String decode(byte[] bytes, boolean utf8Only) {
		if (utf8Only) {
			return new String(bytes, StandardCharsets.UTF_8);
		}
		try {
			return StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(bytes))
					.toString();
		} catch (CharacterCodingException e) {
			return new String(bytes, StandardCharsets.ISO_8859_1);
		}
	}
}
