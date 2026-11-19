package com.ravecraft.mod.client.library;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.core.library.LibraryFolders;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;

/** Loads and saves the chosen library folders in the config directory. Errors are logged, never thrown. */
public final class LibraryFoldersStore {
	private static LibraryFolders cached;

	private LibraryFoldersStore() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("ravecraft-library.json");
	}

	public static synchronized LibraryFolders get() {
		if (cached == null) {
			cached = load();
		}
		return cached;
	}

	public static synchronized void set(LibraryFolders folders) {
		cached = folders;
		Path file = file();
		try {
			Files.createDirectories(file.getParent());
			Path temp = Files.createTempFile(file.getParent(), "ravecraft-library", ".tmp");
			Files.writeString(temp, folders.toJson(), StandardCharsets.UTF_8);
			try {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			RaveCraft.LOGGER.warn("Could not save the library folders to {}", file, e);
		}
	}

	private static LibraryFolders load() {
		Path file = file();
		if (!Files.exists(file)) {
			return LibraryFolders.firstRun(Path.of(System.getProperty("user.home")));
		}
		try {
			return LibraryFolders.fromJson(Files.readString(file, StandardCharsets.UTF_8));
		} catch (IOException e) {
			RaveCraft.LOGGER.warn("Could not read the library folders from {}", file, e);
			return LibraryFolders.empty();
		}
	}
}
