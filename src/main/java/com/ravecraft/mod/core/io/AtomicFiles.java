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
		void write(Writer out) throws IOException;
	}

	private AtomicFiles() {
	}

	/**
	 * Writes {@code content} in UTF-8 to a temporary file next to {@code file}, then moves it over {@code file}
	 * (atomically when the filesystem can). The temporary file is removed when anything fails.
	 */
	public static void write(Path file, Content content) throws IOException {
		Path dir = file.toAbsolutePath().getParent();
		Files.createDirectories(dir);
		Path temp = Files.createTempFile(dir, file.getFileName().toString(), ".tmp");
		try {
			try (Writer out = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
				content.write(out);
			}
			try {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temp);
		}
	}

	/** {@link #write(Path, Content)} with a whole string. */
	public static void write(Path file, String text) throws IOException {
		write(file, out -> out.write(text));
	}
}
