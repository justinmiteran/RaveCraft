package com.ravecraft.mod.core.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ravecraft.mod.core.library.SourceNode.Kind;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LibraryIndexTest {
	private static final Path ROOT = Path.of("music").toAbsolutePath();
	private static final Path CHILL = ROOT.resolve("Chill");
	private static final Path DEEP = CHILL.resolve("Deep");

	private static SourceNode track(Path path) {
		return new SourceNode(path.toString(), Kind.TRACK, AudioFiles.baseName(path), -1, false);
	}

	private static SourceNode playlist(Path path) {
		return new SourceNode(path.toString(), Kind.PLAYLIST, path.getFileName().toString(), -1, false);
	}

	private static LibraryIndex sample() {
		LibraryIndex index = new LibraryIndex(List.of(ROOT), List.of());
		index.add(List.of(track(DEEP.resolve("b.mp3")), track(ROOT.resolve("a.ogg"))));
		index.add(List.of(track(CHILL.resolve("c.flac")), playlist(CHILL.resolve("mix.m3u"))));
		return index;
	}

	@Test
	void countsEveryTrackUnderAFolderNotJustItsOwn() {
		LibraryIndex index = sample();
		assertEquals(3, index.countUnder(ROOT.toString()));
		assertEquals(2, index.countUnder(CHILL.toString()), "the playlist is not a track");
		assertEquals(1, index.countUnder(DEEP.toString()));
		assertEquals(0, index.countUnder(ROOT.resolve("Empty").toString()));
	}

	@Test
	void entriesAreSortedByNameWhateverTheOrderTheyCameIn() {
		assertEquals(List.of("a", "b", "c", "mix.m3u"), sample().entries().stream().map(SourceNode::name).toList());
	}

	@Test
	void theVersionChangesOnEveryAddSoViewsKnowWhenToRebuild() {
		LibraryIndex index = new LibraryIndex(List.of(ROOT), List.of());
		int before = index.version();
		index.add(List.of(track(ROOT.resolve("a.ogg"))));
		assertTrue(index.version() != before);
	}

	@Test
	void aNewIndexIsIncompleteUntilItsScanEnds() {
		LibraryIndex index = sample();
		assertFalse(index.complete());
		index.finish(false);
		assertTrue(index.complete());
		assertFalse(index.truncated());
		LibraryIndex big = sample();
		big.finish(true);
		assertTrue(big.truncated());
	}

	@Test
	void writtenAndReadBackGivesTheSameIndex() throws IOException {
		LibraryIndex index = sample();
		index.finish(true);
		StringWriter out = new StringWriter();
		index.write(out);
		LibraryIndex read = LibraryIndex.read(new StringReader(out.toString())).orElseThrow();
		assertEquals(index.entries(), read.entries());
		assertEquals(index.scope(), read.scope());
		assertTrue(read.complete());
		assertTrue(read.truncated());
		assertEquals(3, read.countUnder(ROOT.toString()));
	}

	@Test
	void aFileOfAnotherFormatIsNotRead() throws IOException {
		assertEquals(Optional.empty(), LibraryIndex.read(new StringReader("something else\n")));
		assertEquals(Optional.empty(), LibraryIndex.read(new StringReader("")));
	}

	@Test
	void restrictingToNewFoldersDropsWhatIsNoLongerChosenOrIsExcluded() {
		LibraryIndex index = sample();
		index.finish(false);
		Path other = Path.of("other").toAbsolutePath();
		LibraryIndex restricted = index.restrictTo(new LibraryIndex.Scope(List.of(ROOT, other), List.of(DEEP)));
		assertEquals(List.of("a", "c", "mix.m3u"), restricted.entries().stream().map(SourceNode::name).toList());
		assertEquals(new LibraryIndex.Scope(List.of(ROOT, other), List.of(DEEP)), restricted.scope());
		assertFalse(restricted.complete(), "the new folder still has to be walked");
		assertEquals(0, restricted.countUnder(DEEP.toString()));
	}

	@Test
	void restrictingToTheSameScopeKeepsItComplete() {
		LibraryIndex index = sample();
		index.finish(false);
		assertTrue(index.restrictTo(index.scope()).complete());
	}

	@Test
	void tracksUnderAFolderComeFolderByFolderInNameOrderUpToTheMax() {
		LibraryIndex index = sample();
		assertEquals(List.of(ROOT.resolve("a.ogg"), CHILL.resolve("c.flac"), DEEP.resolve("b.mp3")),
				index.tracksUnder(ROOT.toString(), 10), "a folder's own tracks first, then each subfolder's");
		assertEquals(List.of(CHILL.resolve("c.flac"), DEEP.resolve("b.mp3")), index.tracksUnder(CHILL.toString(), 10));
		assertEquals(List.of(ROOT.resolve("a.ogg")), index.tracksUnder(ROOT.toString(), 1));
		assertEquals(List.of(), index.tracksUnder(ROOT.resolve("Empty").toString(), 10));
	}
}
