package com.ravecraft.mod.core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicFilesTest {
	@TempDir
	Path dir;

	private List<Path> files() throws IOException {
		try (Stream<Path> stream = Files.list(dir)) {
			return stream.toList();
		}
	}

	@Test
	void replacesTheFileAndLeavesNoTemporaryFile() throws IOException {
		Path file = dir.resolve("config").resolve("a.json");
		AtomicFiles.write(file, "old");
		AtomicFiles.write(file, "new é");
		assertEquals("new é", Files.readString(file));
		assertEquals(List.of(file), Files.list(file.getParent()).toList());
	}

	@Test
	void aFailedWriteKeepsTheOldFile() throws IOException {
		Path file = dir.resolve("a.json");
		AtomicFiles.write(file, "old");
		assertThrows(IOException.class, () -> AtomicFiles.write(file, out -> {
			out.write("half");
			throw new IOException("disk full");
		}));
		assertEquals("old", Files.readString(file));
		assertEquals(List.of(file), files());
	}
}
