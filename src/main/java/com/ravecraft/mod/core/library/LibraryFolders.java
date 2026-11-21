package com.ravecraft.mod.core.library;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The folders the player chose to show in the library, and the subfolders of those they excluded (hidden from the
 * library with everything under them). Immutable; paths are absolute and normalized.
 */
public final class LibraryFolders {
	public static final int MAX = 32;
	private static final Gson GSON = new Gson();
	private static final LibraryFolders EMPTY = new LibraryFolders(List.of(), List.of());

	/** The outcome of adding a folder. */
	public enum Result {
		ADDED, DUPLICATE, INSIDE_EXISTING, REPLACED_CHILDREN, FULL,
		/** An exclusion that is not strictly inside a chosen folder. */
		OUTSIDE_CHOSEN
	}

	/** The outcome of {@link #add} or {@link #exclude} and the lists to keep (unchanged unless added or replaced). */
	public record Added(Result result, LibraryFolders folders) {
	}

	private final List<Path> paths;
	private final List<Path> excluded;

	private LibraryFolders(List<Path> paths, List<Path> excluded) {
		this.paths = List.copyOf(paths);
		this.excluded = List.copyOf(excluded);
	}

	/** No folder chosen. */
	public static LibraryFolders empty() {
		return EMPTY;
	}

	/** The given folders made absolute, without duplicates and up to the maximum. */
	public static LibraryFolders of(List<Path> paths) {
		List<Path> list = new ArrayList<>();
		for (Path path : paths) {
			Path normalized = path.toAbsolutePath().normalize();
			if (!list.contains(normalized) && list.size() < MAX) {
				list.add(normalized);
			}
		}
		return new LibraryFolders(list, List.of());
	}

	/** The chosen folders, absolute and normalized. */
	public List<Path> paths() {
		return paths;
	}

	/** The excluded folders, each strictly inside a chosen folder. */
	public List<Path> excluded() {
		return excluded;
	}

	/** Adds {@code path}, replacing chosen folders inside it; the result says what happened. */
	public Added add(Path path) {
		List<Path> added = addTo(paths, path.toAbsolutePath().normalize());
		return added == null ? new Added(refusal(paths, path), this)
				: new Added(added.size() <= paths.size() ? Result.REPLACED_CHILDREN : Result.ADDED,
						new LibraryFolders(added, excluded));
	}

	/** Hides a subfolder of a chosen folder, and everything under it, from the library. */
	public Added exclude(Path path) {
		Path folder = path.toAbsolutePath().normalize();
		if (!insideChosen(paths, folder)) {
			return new Added(Result.OUTSIDE_CHOSEN, this);
		}
		List<Path> added = addTo(excluded, folder);
		return added == null ? new Added(refusal(excluded, folder), this)
				: new Added(added.size() <= excluded.size() ? Result.REPLACED_CHILDREN : Result.ADDED,
						new LibraryFolders(paths, added));
	}

	/** True when {@code folder} is a chosen folder or lies inside one, so adding it would change nothing. */
	public boolean covers(Path folder) {
		Path at = folder.toAbsolutePath().normalize();
		for (Path chosen : paths) {
			if (at.startsWith(chosen)) {
				return true;
			}
		}
		return false;
	}

	/** Whether {@code folder} itself is excluded. */
	public boolean isExcluded(Path folder) {
		return excluded.contains(folder.toAbsolutePath().normalize());
	}

	/** True when an excluded folder lies strictly inside {@code folder}. */
	public boolean holdsExclusion(Path folder) {
		Path at = folder.toAbsolutePath().normalize();
		for (Path hidden : excluded) {
			if (hidden.startsWith(at) && !hidden.equals(at)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The folders to expand, top down, so every exclusion inside {@code folder} shows: {@code folder} itself and
	 * each folder between it and an excluded one.
	 */
	public List<Path> foldersToReveal(Path folder) {
		Path at = folder.toAbsolutePath().normalize();
		List<Path> open = new ArrayList<>();
		for (Path hidden : excluded) {
			if (!hidden.startsWith(at) || hidden.equals(at)) {
				continue;
			}
			for (Path step = at; !step.equals(hidden); step = step.resolve(hidden.getName(step.getNameCount()))) {
				if (!open.contains(step)) {
					open.add(step);
				}
			}
		}
		open.sort(java.util.Comparator.comparingInt(Path::getNameCount));
		return open;
	}

	/** Shows an excluded folder again. */
	public LibraryFolders include(Path path) {
		Path folder = path.toAbsolutePath().normalize();
		if (!excluded.contains(folder)) {
			return this;
		}
		List<Path> kept = new ArrayList<>(excluded);
		kept.remove(folder);
		return new LibraryFolders(paths, kept);
	}

	/** Drops a chosen folder and the exclusions inside it. */
	public LibraryFolders remove(Path path) {
		Path folder = path.toAbsolutePath().normalize();
		if (!paths.contains(folder)) {
			return this;
		}
		List<Path> kept = new ArrayList<>(paths);
		kept.remove(folder);
		List<Path> keptExcluded = new ArrayList<>();
		for (Path hidden : excluded) {
			if (!hidden.startsWith(folder)) {
				keptExcluded.add(hidden);
			}
		}
		return new LibraryFolders(kept, keptExcluded);
	}

	/**
	 * {@code list} with {@code folder} added in place of the entries inside it, or null when it is already there,
	 * inside an entry, or the list is full.
	 */
	private static @Nullable List<Path> addTo(List<Path> list, Path folder) {
		for (Path existing : list) {
			if (folder.startsWith(existing)) {
				return null;
			}
		}
		List<Path> kept = new ArrayList<>();
		for (Path existing : list) {
			if (!existing.startsWith(folder)) {
				kept.add(existing);
			}
		}
		if (kept.size() == list.size() && list.size() >= MAX) {
			return null;
		}
		kept.add(folder);
		return kept;
	}

	/** Why {@link #addTo} refused {@code path}. */
	private static Result refusal(List<Path> list, Path path) {
		Path folder = path.toAbsolutePath().normalize();
		if (list.contains(folder)) {
			return Result.DUPLICATE;
		}
		for (Path existing : list) {
			if (folder.startsWith(existing)) {
				return Result.INSIDE_EXISTING;
			}
		}
		return Result.FULL;
	}

	private static boolean insideChosen(List<Path> chosen, Path folder) {
		for (Path root : chosen) {
			if (folder.startsWith(root) && !folder.equals(root)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Malformed or unexpected JSON gives empty lists; bad entries are skipped, each list is capped at MAX, and
	 * exclusions outside the chosen folders are dropped.
	 */
	public static LibraryFolders fromJson(String json) {
		try {
			JsonElement root = JsonParser.parseString(json);
			if (!root.isJsonObject()) {
				return EMPTY;
			}
			List<Path> folders = pathsOf(root.getAsJsonObject().get("folders"));
			List<Path> excluded = new ArrayList<>();
			for (Path hidden : pathsOf(root.getAsJsonObject().get("excluded"))) {
				if (insideChosen(folders, hidden)) {
					excluded.add(hidden);
				}
			}
			return new LibraryFolders(folders, excluded);
		} catch (RuntimeException e) {
			return EMPTY;
		}
	}

	/** The valid absolute paths of a JSON array, normalized, without duplicates, at most MAX. */
	private static List<Path> pathsOf(@Nullable JsonElement array) {
		List<Path> list = new ArrayList<>();
		if (array == null || !array.isJsonArray()) {
			return list;
		}
		for (JsonElement element : array.getAsJsonArray()) {
			if (list.size() >= MAX) {
				break;
			}
			Path path = absolute(element);
			if (path != null && !list.contains(path)) {
				list.add(path);
			}
		}
		return list;
	}

	/** The normalized absolute path a JSON string holds; null for anything else, a blank or an invalid path. */
	private static @Nullable Path absolute(JsonElement element) {
		if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			return null;
		}
		String text = element.getAsString();
		if (text.isBlank()) {
			return null;
		}
		try {
			Path raw = Path.of(text);
			return raw.isAbsolute() ? raw.normalize() : null;
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** The chosen and excluded folders as the JSON {@link #fromJson} reads. */
	public String toJson() {
		JsonObject root = new JsonObject();
		root.add("folders", array(paths));
		root.add("excluded", array(excluded));
		return GSON.toJson(root);
	}

	private static JsonArray array(List<Path> paths) {
		JsonArray array = new JsonArray();
		for (Path path : paths) {
			array.add(path.toString());
		}
		return array;
	}

	/** The user's Music folder when it exists, otherwise nothing. */
	public static LibraryFolders firstRun(Path home) {
		Path music = home.resolve("Music");
		return Files.isDirectory(music) ? of(List.of(music)) : EMPTY;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof LibraryFolders folders && paths.equals(folders.paths)
				&& excluded.equals(folders.excluded);
	}

	@Override
	public int hashCode() {
		return 31 * paths.hashCode() + excluded.hashCode();
	}

	@Override
	public String toString() {
		return "LibraryFolders" + paths + " excluded " + excluded;
	}
}
