package com.ravecraft.mod.core.io;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Replaces a text file as a whole: a crash or an error never leaves half a file behind. */
public final class AtomicFiles {
	/** Writes the new content; may throw, the file is then left as it was. */
	@FunctionalInterface
	public interface Content {
		/**
		 * Writes the whole content to {@code out}.
		 *
		 * @param out where the content goes
		 */
		void write(Writer out) throws IOException;
	}

	private AtomicFiles() {
	}

	/**
	 * Writes {@code content} in UTF-8 to a temporary file next to {@code file}, then moves it over {@code file}
	 * (atomically when the filesystem can). The temporary file is removed when anything fails.
	 *
	 * @param file the file to write
	 * @param content writes the content
	 */
	public static void write(Path file, Content content) throws IOException {
		Path dir = file.toAbsolutePath().getParent();
		Files.createDirectories(dir);
		Path temp = Files.createTempFile(dir, file.getFileName().toString(), ".tmp");
		try {
			fill(temp, content);
			replace(temp, file);
		} finally {
			Files.deleteIfExists(temp);
		}
	}

	private static void fill(Path temp, Content content) throws IOException {
		try (Writer out = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
			content.write(out);
		}
	}

	/** Moves {@code temp} over {@code file}, atomically when the filesystem can. */
	private static void replace(Path temp, Path file) throws IOException {
		try {
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/**
	 * {@link #write(Path, Content)} with a whole string.
	 *
	 * @param file the file to write
	 * @param text the content
	 */
	public static void write(Path file, String text) throws IOException {
		write(file, out -> out.write(text));
	}
}
