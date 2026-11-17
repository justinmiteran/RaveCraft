package com.ravecraft.mod.core.library;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AudioFilesTest {
	@Test
	void recognisesAudioExtensionsIgnoringCase() {
		assertTrue(AudioFiles.isAudio(Path.of("song.MP3")));
		assertTrue(AudioFiles.isAudio(Path.of("a.flac")));
		assertTrue(AudioFiles.isAudio(Path.of("b.oga")));
		assertTrue(AudioFiles.isAudio(Path.of("c.ogg")));
		assertTrue(AudioFiles.isAudio(Path.of("d.Wav")));
		assertFalse(AudioFiles.isAudio(Path.of("c.txt")));
		assertFalse(AudioFiles.isAudio(Path.of("noext")));
		assertFalse(AudioFiles.isAudio(Path.of("list.m3u")));
	}

	@Test
	void recognisesPlaylists() {
		assertTrue(AudioFiles.isPlaylist(Path.of("x.M3U8")));
		assertTrue(AudioFiles.isPlaylist(Path.of("y.m3u")));
		assertFalse(AudioFiles.isPlaylist(Path.of("z.mp3")));
	}
}
