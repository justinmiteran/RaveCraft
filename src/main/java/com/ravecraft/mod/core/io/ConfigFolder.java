package com.ravecraft.mod.core.io;

import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * The mod's own folder inside the game's config directory. Up to 0.1.0 the files were directly in the config
 * directory, named {@code ravecraft-<name>}; {@link #migrate} brings them in.
 */
public final class ConfigFolder {
	private static final String FOLDER = "ravecraft";
	private static final String OLD_PREFIX = "ravecraft-";
	/** The old files, by exact name so that another mod's {@code ravecraft-…} file stays where it is. */
	private static final String OLD_FILES =
			OLD_PREFIX + "{server.json5,server.json5.bak,tracks.json,library.json,library-index-*.txt}";

	private final Path configDir;

	/**
	 * Describes the mod's folder; nothing is created.
	 *
	 * @param configDir the game's config directory
	 */
	public ConfigFolder(Path configDir) {
		this.configDir = configDir;
	}

	/**
	 * Where a config file of the mod is, whether it exists or not.
	 *
	 * @param name the file name
	 * @return the file, in the mod's folder
	 */
	public Path file(String name) {
		return configDir.resolve(FOLDER).resolve(name);
	}

	/**
	 * Moves the files of older versions into the folder. A file that already exists there is never replaced.
	 *
	 * @param failed told of each file that could not be moved, which is left where it was
	 */
	public void migrate(BiConsumer<Path, IOException> failed) {
		if (!Files.isDirectory(configDir)) {
			return;
		}
		List<Path> oldFiles;
		try {
			oldFiles = oldFiles();
		} catch (IOException e) {
			failed.accept(configDir, e);
			return;
		}
		for (Path old : oldFiles) {
			move(old, failed);
		}
	}

	private List<Path> oldFiles() throws IOException {
		List<Path> found = new ArrayList<>();
		try (DirectoryStream<Path> files = Files.newDirectoryStream(configDir, OLD_FILES)) {
			files.forEach(found::add);
		} catch (DirectoryIteratorException e) {
			throw e.getCause();
		}
		return found;
	}

	private void move(Path old, BiConsumer<Path, IOException> failed) {
		Path file = file(old.getFileName().toString().substring(OLD_PREFIX.length()));
		if (Files.exists(file)) {
			return;
		}
		try {
			Files.createDirectories(file.getParent());
			Files.move(old, file);
		} catch (IOException e) {
			failed.accept(old, e);
		}
	}
}
