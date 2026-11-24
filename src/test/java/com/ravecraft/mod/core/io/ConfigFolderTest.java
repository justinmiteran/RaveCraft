package com.ravecraft.mod.core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigFolderTest {
	@TempDir
	Path configDir;
	private ConfigFolder folder;

	@BeforeEach
	void create() {
		folder = new ConfigFolder(configDir);
	}

	@Test
	void putsFilesInTheModFolder() {
		assertEquals(configDir.resolve("ravecraft").resolve("server.json5"), folder.file("server.json5"));
	}

	@Test
	void movesEveryOldFile() throws IOException {
		List<String> names = List.of("server.json5", "server.json5.bak", "tracks.json", "library.json",
				"library-index-local.txt");
		for (String name : names) {
			Files.writeString(configDir.resolve("ravecraft-" + name), name);
		}
		migrate();
		for (String name : names) {
			assertEquals(name, Files.readString(folder.file(name)));
			assertFalse(Files.exists(configDir.resolve("ravecraft-" + name)), name);
		}
	}

	@Test
	void keepsTheNewFileWhenBothExist() throws IOException {
		Path old = configDir.resolve("ravecraft-library.json");
		Files.writeString(old, "old");
		Files.createDirectories(configDir.resolve("ravecraft"));
		Files.writeString(folder.file("library.json"), "new");
		migrate();
		assertEquals("new", Files.readString(folder.file("library.json")));
		assertEquals("old", Files.readString(old));
	}

	@Test
	void leavesOtherFilesAlone() throws IOException {
		Path addon = configDir.resolve("ravecraft-addon.toml");
		Path lookalike = configDir.resolve("ravecraft-tracks.json.txt");
		Files.writeString(addon, "addon");
		Files.writeString(lookalike, "mine");
		migrate();
		assertTrue(Files.exists(addon));
		assertTrue(Files.exists(lookalike));
		assertFalse(Files.exists(configDir.resolve("ravecraft")));
	}

	@Test
	void doesNothingWithoutAConfigDirectory() {
		new ConfigFolder(configDir.resolve("missing")).migrate((path, error) -> fail(path.toString(), error));
		assertFalse(Files.exists(configDir.resolve("missing")));
	}

	@Test
	void reportsAFileItCannotMoveAndContinues() throws IOException {
		// A plain file where the folder should be: no file can be moved into it.
		Files.writeString(configDir.resolve("ravecraft"), "in the way");
		Files.writeString(configDir.resolve("ravecraft-library.json"), "library");
		Files.writeString(configDir.resolve("ravecraft-tracks.json"), "tracks");
		List<String> failed = new ArrayList<>();
		folder.migrate((path, error) -> failed.add(path.getFileName().toString()));
		failed.sort(null);
		assertEquals(List.of("ravecraft-library.json", "ravecraft-tracks.json"), failed);
		assertEquals("library", Files.readString(configDir.resolve("ravecraft-library.json")));
	}

	private void migrate() {
		folder.migrate((path, error) -> fail(path.toString(), error));
	}
}
