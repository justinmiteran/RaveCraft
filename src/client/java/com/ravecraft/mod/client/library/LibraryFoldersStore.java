package com.ravecraft.mod.client.library;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.core.io.AtomicFiles;
import com.ravecraft.mod.core.library.LibraryFolders;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;

/** Loads and saves the chosen library folders in the config directory. Errors are logged, never thrown. */
public final class LibraryFoldersStore {
	private static LibraryFolders cached;
	/** The last save, so saves run in order. */
	private static CompletableFuture<Void> saving = CompletableFuture.completedFuture(null);

	private LibraryFoldersStore() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("ravecraft-library.json");
	}

	/** The chosen folders, read from their file the first time. */
	public static synchronized LibraryFolders get() {
		if (cached == null) {
			cached = load();
		}
		return cached;
	}

	/** Keeps {@code folders} at once and saves them off thread, one save after the other. */
	public static synchronized void set(LibraryFolders folders) {
		cached = folders;
		Path file = file();
		String json = folders.toJson();
		saving = saving.thenRunAsync(() -> {
			try {
				AtomicFiles.write(file, json);
			} catch (IOException e) {
				RaveCraft.LOGGER.warn("Could not save the library folders to {}", file, e);
			}
		}, Util.ioPool());
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
