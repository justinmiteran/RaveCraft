package com.ravecraft.mod.core.library;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ravecraft.mod.core.library.LibraryEntry.Kind;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SourceNodeTest {
	@Test
	void aDriveOrFolderEntryBecomesAFolderNode() {
		Path drive = Path.of("drive").toAbsolutePath();
		assertEquals(new SourceNode(drive.toString(), SourceNode.Kind.FOLDER, "D:", false),
				SourceNode.of(new LibraryEntry(drive, Kind.ROOT, "D:")));
		assertEquals(SourceNode.Kind.FOLDER, SourceNode.of(new LibraryEntry(drive, Kind.FOLDER, "x")).kind());
		assertEquals(SourceNode.Kind.PLAYLIST, SourceNode.of(new LibraryEntry(drive, Kind.PLAYLIST, "x")).kind());
		assertEquals(SourceNode.Kind.TRACK, SourceNode.of(new LibraryEntry(drive, Kind.TRACK, "x")).kind());
	}
}
