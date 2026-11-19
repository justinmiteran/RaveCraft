package com.ravecraft.mod.core.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ravecraft.mod.core.library.SourceNode.Kind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFolderSourceTest {
	@TempDir
	Path dir;

	@Test
	void missingRootIsMarkedMissing() throws IOException {
		Path root = Files.createDirectory(dir.resolve("gone"));
		LocalFolderSource source = new LocalFolderSource(() -> List.of(root));
		assertFalse(source.roots().get(0).missing());
		Files.delete(root);
		SourceNode node = source.roots().get(0);
		assertTrue(node.missing());
		assertEquals(0, node.trackCount());
		assertEquals(List.of(), source.children(node));
	}

	@Test
	void childrenListsFoldersThenTracks() throws IOException {
		Files.createDirectory(dir.resolve("sub"));
		Files.createFile(dir.resolve("b.mp3"));
		Files.createFile(dir.resolve("a.ogg"));
		Files.createFile(dir.resolve("notes.txt"));
		Files.writeString(dir.resolve("mix.m3u"), "a.ogg\n");
		LocalFolderSource source = new LocalFolderSource(() -> List.of(dir));
		List<SourceNode> children = source.children(source.roots().get(0));
		assertEquals(List.of(
				new SourceNode(dir.resolve("sub").toString(), Kind.FOLDER, "sub", 0, false),
				new SourceNode(dir.resolve("mix.m3u").toString(), Kind.PLAYLIST, "mix.m3u", -1, false),
				new SourceNode(dir.resolve("a.ogg").toString(), Kind.TRACK, "a", -1, false),
				new SourceNode(dir.resolve("b.mp3").toString(), Kind.TRACK, "b", -1, false)), children);
		assertEquals(List.of(dir.resolve("a.ogg")), source.tracksOf(children.get(1)));
		assertEquals(List.of(dir.resolve("b.mp3")), source.tracksOf(children.get(3)));
	}

	@Test
	void folderCountsDirectTracks() throws IOException {
		Path sub = Files.createDirectory(dir.resolve("sub"));
		Files.createFile(dir.resolve("one.mp3"));
		Files.createFile(dir.resolve("two.flac"));
		Files.createFile(dir.resolve("skip.txt"));
		Files.createFile(sub.resolve("deep.mp3"));
		LocalFolderSource source = new LocalFolderSource(() -> List.of(dir));
		SourceNode root = source.roots().get(0);
		assertEquals(2, root.trackCount());
		assertEquals(dir.getFileName().toString(), root.name());
		assertEquals(1, source.children(root).get(0).trackCount());
		assertEquals(List.of(dir.resolve("one.mp3"), dir.resolve("two.flac")), source.tracksOf(root));
	}

	@Test
	void childrenOfVanishedFolderIsEmpty() throws IOException {
		Path root = Files.createDirectory(dir.resolve("temp"));
		LocalFolderSource source = new LocalFolderSource(() -> List.of(root));
		SourceNode node = source.roots().get(0);
		Files.delete(root);
		assertEquals(List.of(), source.children(node));
	}

	@Test
	void allTracksListsTracksAndPlaylistsOfEverySubfolderByName() throws IOException {
		Path sub = Files.createDirectories(dir.resolve("sub").resolve("deeper"));
		Files.createFile(dir.resolve("b.mp3"));
		Files.createFile(dir.resolve("A.ogg"));
		Files.createFile(dir.resolve("notes.txt"));
		Files.writeString(dir.resolve("mix.m3u"), "b.mp3" + System.lineSeparator());
		Files.createFile(sub.resolve("c.flac"));
		LocalFolderSource source = new LocalFolderSource(() -> List.of(dir));
		assertEquals(List.of(
				new SourceNode(dir.resolve("A.ogg").toString(), Kind.TRACK, "A", -1, false),
				new SourceNode(dir.resolve("b.mp3").toString(), Kind.TRACK, "b", -1, false),
				new SourceNode(sub.resolve("c.flac").toString(), Kind.TRACK, "c", -1, false),
				new SourceNode(dir.resolve("mix.m3u").toString(), Kind.PLAYLIST, "mix.m3u", -1, false)),
				source.allTracks());
	}

	@Test
	void allTracksSkipsMissingRootsAndListsNestedRootsOnce() throws IOException {
		Path sub = Files.createDirectory(dir.resolve("sub"));
		Files.createFile(sub.resolve("deep.mp3"));
		LocalFolderSource source = new LocalFolderSource(() -> List.of(dir, sub, dir.resolve("gone")));
		assertEquals(List.of(new SourceNode(sub.resolve("deep.mp3").toString(), Kind.TRACK, "deep", -1, false)),
				source.allTracks());
	}

	@Test
	void allTracksStopsAtTheLimit() throws IOException {
		Files.createFile(dir.resolve("a.mp3"));
		Files.createFile(dir.resolve("b.mp3"));
		Files.createFile(dir.resolve("c.mp3"));
		LocalFolderSource source = new LocalFolderSource(() -> List.of(dir));
		assertEquals(2, source.allTracks(2).size());
	}

	@Test
	void parentNameIsTheContainingFolder() throws IOException {
		Path sub = Files.createDirectory(dir.resolve("Chill"));
		Files.createFile(sub.resolve("night.mp3"));
		LocalFolderSource source = new LocalFolderSource(() -> List.of(dir));
		assertEquals("Chill", source.parentName(source.allTracks().get(0)));
	}
}
