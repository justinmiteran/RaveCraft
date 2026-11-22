package com.ravecraft.mod.core.library;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The folders the player excluded, compared as absolute normalized paths. */
public final class Exclusions {
	private final Set<Path> hidden = new HashSet<>();

	/**
	 * Hides {@code excluded} and everything under them.
	 *
	 * @param excluded the excluded folders
	 */
	public Exclusions(List<Path> excluded) {
		for (Path folder : excluded) {
			hidden.add(normalize(folder));
		}
	}

	/**
	 * True when {@code folder} itself was excluded (not merely something under it).
	 *
	 * @param folder the folder
	 * @return true when the folder is excluded
	 */
	public boolean hides(Path folder) {
		return hidden.contains(normalize(folder));
	}

	private static Path normalize(Path path) {
		return path.toAbsolutePath().normalize();
	}
}
