package com.ravecraft.mod.core.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ravecraft.mod.core.library.SourceNode.Kind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FlatScanTest {
	@TempDir
	Path dir;

	private static List<String> names(List<SourceNode> nodes) {
		return nodes.stream().map(SourceNode::name).sorted(Comparator.naturalOrder()).toList();
	}

	@Test
	void findsTracksAndPlaylistsOfEverySubfolder() throws IOException {
		Path deeper = Files.createDirectories(dir.resolve("sub").resolve("deeper"));
		Files.createFile(dir.resolve("b.mp3"));
		Files.createFile(dir.resolve("notes.txt"));
		Files.writeString(dir.resolve("mix.m3u"), "b.mp3" + System.lineSeparator());
		Files.createFile(deeper.resolve("c.flac"));
		List<SourceNode> found = new ArrayList<>();
		assertFalse(new FlatScan(List.of(dir)).run(found::addAll, 64), "nothing left");
		assertEquals(List.of("b", "c", "mix.m3u"), names(found));
		assertTrue(found.contains(new SourceNode(deeper.resolve("c.flac").toString(), Kind.TRACK, "c", -1, false)));
		assertTrue(found.contains(new SourceNode(dir.resolve("mix.m3u").toString(), Kind.PLAYLIST, "mix.m3u", -1,
				false)));
	}

	@Test
	void sendsTheTracksInBatches() throws IOException {
		for (String name : List.of("a", "b", "c", "d", "e")) {
			Files.createFile(dir.resolve(name + ".mp3"));
		}
		List<Integer> sizes = new ArrayList<>();
		new FlatScan(List.of(dir)).run(batch -> sizes.add(batch.size()), 2);
		assertEquals(List.of(2, 2, 1), sizes);
	}

	@Test
	void skipsSystemAndSoftwareFoldersFoundOnTheWay() throws IOException {
		for (String name : List.of("Windows", "Program Files", "AppData", "node_modules", "Music")) {
			Files.createFile(Files.createDirectory(dir.resolve(name)).resolve(name + ".wav"));
		}
		List<SourceNode> found = new ArrayList<>();
		new FlatScan(List.of(dir)).run(found::addAll, 64);
		assertEquals(List.of("Music"), names(found));
	}

	@Test
	void skipsTheFoldersThePlayerExcludedAndTheirSubfolders() throws IOException {
		Path jingles = Files.createDirectories(dir.resolve("Jingles").resolve("Old"));
		Files.createFile(dir.resolve("Jingles").resolve("ding.mp3"));
		Files.createFile(jingles.resolve("dong.mp3"));
		Files.createFile(Files.createDirectory(dir.resolve("Chill")).resolve("calm.mp3"));
		List<SourceNode> found = new ArrayList<>();
		new FlatScan(List.of(dir), List.of(dir.resolve("Jingles"))).run(found::addAll, 64);
		assertEquals(List.of("calm"), names(found));
	}

	@Test
	void walksAnExcludedFolderThePlayerChoseThemself() throws IOException {
		Path media = Files.createDirectory(dir.resolve("Windows"));
		Files.createFile(media.resolve("chimes.wav"));
		List<SourceNode> found = new ArrayList<>();
		new FlatScan(List.of(media)).run(found::addAll, 64);
		assertEquals(List.of("chimes"), names(found));
	}

	@Test
	void skipsMissingRootsAndWalksNestedRootsOnce() throws IOException {
		Path sub = Files.createDirectory(dir.resolve("sub"));
		Files.createFile(sub.resolve("deep.mp3"));
		List<SourceNode> found = new ArrayList<>();
		new FlatScan(List.of(dir, sub, dir.resolve("gone"))).run(found::addAll, 64);
		assertEquals(List.of("deep"), names(found));
	}

	@Test
	void stopsAfterAPageAndResumesWhereItStopped() throws IOException {
		for (String folder : List.of("a", "b")) {
			Path sub = Files.createDirectory(dir.resolve(folder));
			Files.createFile(sub.resolve(folder + "1.mp3"));
			Files.createFile(sub.resolve(folder + "2.mp3"));
		}
		FlatScan scan = new FlatScan(List.of(dir), 2);
		List<SourceNode> found = new ArrayList<>();
		assertTrue(scan.run(found::addAll, 64), "more is left");
		assertEquals(List.of("a1", "a2"), names(found));
		scan.nextPage();
		assertFalse(scan.run(found::addAll, 64));
		assertEquals(List.of("a1", "a2", "b1", "b2"), names(found));
	}

	@Test
	void aPageEndsOnlyWhenItFoundEnoughEntriesNotAfterManyFolders() throws IOException {
		for (int i = 0; i < 5; i++) {
			Files.createDirectory(dir.resolve("empty" + i));
		}
		Files.createFile(Files.createDirectory(dir.resolve("z")).resolve("z1.mp3"));
		FlatScan scan = new FlatScan(List.of(dir), 2);
		List<SourceNode> found = new ArrayList<>();
		assertFalse(scan.run(found::addAll, 64), "walking many empty folders does not end the page");
		assertEquals(List.of("z1"), names(found));
	}

	@Test
	void anUnreadableAliasDoesNotHideTheFolderItPointsTo() throws IOException {
		// Like C:\Documents and Settings, a link to C:\Users that cannot be listed: C:\Users is still walked.
		Path real = Files.createDirectory(dir.resolve("real"));
		Files.createDirectory(dir.resolve("alias"));
		Files.createFile(real.resolve("song.mp3"));
		Path viaAlias = dir.resolve("alias").resolve("..").resolve("real");
		FlatScan scan = new FlatScan(List.of(viaAlias, real), List.of(), FlatScan.PAGE, folder -> {
			if (folder.equals(viaAlias)) {
				throw new java.nio.file.AccessDeniedException(folder.toString());
			}
			return MusicLibrary.list(folder);
		});
		List<SourceNode> found = new ArrayList<>();
		scan.run(found::addAll, 64);
		assertEquals(List.of("song"), names(found));
	}

	@Test
	void stopsWhenCancelled() throws IOException {
		for (String folder : List.of("a", "b")) {
			Files.createFile(Files.createDirectory(dir.resolve(folder)).resolve(folder + ".mp3"));
		}
		FlatScan scan = new FlatScan(List.of(dir));
		List<SourceNode> found = new ArrayList<>();
		boolean more = scan.run(batch -> {
			found.addAll(batch);
			scan.cancel();
		}, 1);
		assertFalse(more, "a cancelled scan offers nothing more");
		assertTrue(scan.cancelled());
		assertEquals(1, found.size());
	}
}
