package com.ravecraft.mod.core.library;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ravecraft.mod.core.library.LibraryEntry.Kind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MusicLibraryTest {
	@TempDir
	Path dir;

	@BeforeEach
	void createFolder() throws IOException {
		for (String name : List.of("b.mp3", "A.flac", "notes.txt", ".hidden.mp3")) {
			Files.createFile(dir.resolve(name));
		}
		Files.createDirectory(dir.resolve("zeta"));
		Files.writeString(dir.resolve("mix.m3u"), "b.mp3\nmissing.mp3\nnotes.txt\n");
	}

	@Test
	void listsFoldersThenPlaylistsAndTracks() throws IOException {
		List<LibraryEntry> entries = MusicLibrary.list(dir);
		assertEquals(List.of(
				new LibraryEntry(dir.resolve("zeta"), Kind.FOLDER, "zeta"),
				new LibraryEntry(dir.resolve("mix.m3u"), Kind.PLAYLIST, "mix.m3u"),
				new LibraryEntry(dir.resolve("A.flac"), Kind.TRACK, "A"),
				new LibraryEntry(dir.resolve("b.mp3"), Kind.TRACK, "b")), entries);
	}

	@Test
	void folderTracksAreItsAudioFilesInListOrder() throws IOException {
		LibraryEntry folder = new LibraryEntry(dir, Kind.FOLDER, "dir");
		assertEquals(List.of(dir.resolve("A.flac"), dir.resolve("b.mp3")), MusicLibrary.tracksOf(folder));
	}

	@Test
	void playlistTracksKeepOnlyExistingAudioFiles() throws IOException {
		LibraryEntry playlist = new LibraryEntry(dir.resolve("mix.m3u"), Kind.PLAYLIST, "mix.m3u");
		assertEquals(List.of(dir.resolve("b.mp3").toAbsolutePath().normalize()), MusicLibrary.tracksOf(playlist));
	}

	@Test
	void trackIsItsOwnTrackList() throws IOException {
		LibraryEntry track = new LibraryEntry(dir.resolve("b.mp3"), Kind.TRACK, "b");
		assertEquals(List.of(dir.resolve("b.mp3")), MusicLibrary.tracksOf(track));
	}

	@Test
	void hugeFoldersAreCapped() throws IOException {
		Path big = Files.createDirectory(dir.resolve("big"));
		for (int i = 0; i < MusicLibrary.MAX_ENTRIES + 100; i++) {
			Files.createFile(big.resolve("t" + i + ".mp3"));
		}
		assertEquals(MusicLibrary.MAX_ENTRIES, MusicLibrary.list(big).size());
	}

	@Test
	void folderNodesAreTheSubfoldersOnlyByName() throws IOException {
		Files.createDirectory(dir.resolve("Alpha"));
		assertEquals(List.of(
				new SourceNode(dir.resolve("Alpha").toString(), SourceNode.Kind.FOLDER, "Alpha", -1, false),
				new SourceNode(dir.resolve("zeta").toString(), SourceNode.Kind.FOLDER, "zeta", -1, false)),
				MusicLibrary.folderNodes(dir));
	}
}
