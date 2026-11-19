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

/** The folders the player chose to show in the library. Immutable; paths are absolute and normalized. */
public final class LibraryFolders {
	public static final int MAX = 32;
	private static final Gson GSON = new Gson();
	private static final LibraryFolders EMPTY = new LibraryFolders(List.of());

	public enum Result {
		ADDED, DUPLICATE, INSIDE_EXISTING, REPLACED_CHILDREN, FULL
	}

	/** The outcome of {@link #add} and the list to keep (unchanged unless added or replaced). */
	public record Added(Result result, LibraryFolders folders) {
	}

	private final List<Path> paths;

	private LibraryFolders(List<Path> paths) {
		this.paths = List.copyOf(paths);
	}

	public static LibraryFolders empty() {
		return EMPTY;
	}

	public static LibraryFolders of(List<Path> paths) {
		List<Path> list = new ArrayList<>();
		for (Path path : paths) {
			Path normalized = path.toAbsolutePath().normalize();
			if (!list.contains(normalized) && list.size() < MAX) {
				list.add(normalized);
			}
		}
		return new LibraryFolders(list);
	}

	public List<Path> paths() {
		return paths;
	}

	public Added add(Path path) {
		Path folder = path.toAbsolutePath().normalize();
		if (paths.contains(folder)) {
			return new Added(Result.DUPLICATE, this);
		}
		for (Path existing : paths) {
			if (folder.startsWith(existing)) {
				return new Added(Result.INSIDE_EXISTING, this);
			}
		}
		List<Path> kept = new ArrayList<>();
		for (Path existing : paths) {
			if (!existing.startsWith(folder)) {
				kept.add(existing);
			}
		}
		boolean replaced = kept.size() < paths.size();
		if (!replaced && paths.size() >= MAX) {
			return new Added(Result.FULL, this);
		}
		kept.add(folder);
		return new Added(replaced ? Result.REPLACED_CHILDREN : Result.ADDED, new LibraryFolders(kept));
	}

	public LibraryFolders remove(Path path) {
		Path folder = path.toAbsolutePath().normalize();
		if (!paths.contains(folder)) {
			return this;
		}
		List<Path> kept = new ArrayList<>(paths);
		kept.remove(folder);
		return new LibraryFolders(kept);
	}

	/** Malformed or unexpected JSON gives an empty list; bad entries are skipped and the list is capped at MAX. */
	public static LibraryFolders fromJson(String json) {
		try {
			JsonElement root = JsonParser.parseString(json);
			if (!root.isJsonObject()) {
				return EMPTY;
			}
			JsonElement folders = root.getAsJsonObject().get("folders");
			if (folders == null || !folders.isJsonArray()) {
				return EMPTY;
			}
			List<Path> list = new ArrayList<>();
			for (JsonElement element : folders.getAsJsonArray()) {
				if (list.size() >= MAX) {
					break;
				}
				if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
					continue;
				}
				try {
					Path path = Path.of(element.getAsString()).toAbsolutePath().normalize();
					if (!list.contains(path)) {
						list.add(path);
					}
				} catch (RuntimeException e) {
					// invalid path: skipped
				}
			}
			return new LibraryFolders(list);
		} catch (RuntimeException e) {
			return EMPTY;
		}
	}

	public String toJson() {
		JsonArray array = new JsonArray();
		for (Path path : paths) {
			array.add(path.toString());
		}
		JsonObject root = new JsonObject();
		root.add("folders", array);
		return GSON.toJson(root);
	}

	/** The user's Music folder when it exists, otherwise nothing. */
	public static LibraryFolders firstRun(Path home) {
		Path music = home.resolve("Music");
		return Files.isDirectory(music) ? of(List.of(music)) : EMPTY;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof LibraryFolders folders && paths.equals(folders.paths);
	}

	@Override
	public int hashCode() {
		return paths.hashCode();
	}

	@Override
	public String toString() {
		return "LibraryFolders" + paths;
	}
}
