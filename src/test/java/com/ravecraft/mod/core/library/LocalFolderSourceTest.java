package com.ravecraft.mod.core.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ravecraft.mod.core.library.SourceNode.Kind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
	void parentNameIsTheContainingFolder() {
		LocalFolderSource source = new LocalFolderSource(() -> List.of(dir));
		SourceNode night = new SourceNode(dir.resolve("Chill").resolve("night.mp3").toString(), Kind.TRACK, "night", -1,
				false);
		assertEquals("Chill", source.parentName(night));
	}

	@Test
	void flatScanWalksTheChosenFolders() throws IOException {
		Files.createFile(dir.resolve("a.mp3"));
		LocalFolderSource source = new LocalFolderSource(() -> List.of(dir));
		List<SourceNode> found = new ArrayList<>();
		source.flatScan().run(found::addAll, 64);
		assertEquals(List.of(new SourceNode(dir.resolve("a.mp3").toString(), Kind.TRACK, "a", -1, false)), found);
	}

	@Test
	void excludedFoldersAreHiddenAndNotWalked() throws IOException {
		Files.createFile(Files.createDirectory(dir.resolve("Jingles")).resolve("ding.mp3"));
		Files.createDirectory(dir.resolve("Chill"));
		LocalFolderSource source = new LocalFolderSource(() -> List.of(dir), () -> List.of(dir.resolve("Jingles")));
		assertEquals(List.of(new SourceNode(dir.resolve("Chill").toString(), Kind.FOLDER, "Chill", 0, false)),
				source.children(source.roots().get(0)));
		List<SourceNode> found = new ArrayList<>();
		source.flatScan().run(found::addAll, 64);
		assertEquals(List.of(), found);
	}
}
