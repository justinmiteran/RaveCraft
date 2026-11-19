package com.ravecraft.mod.core.library;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every track and playlist found under the chosen folders, with how many tracks each folder holds, subfolders
 * included. It is filled batch by batch while a {@link FlatScan} walks the disk, then {@link #finish finished}; it
 * can be saved and read back so the library shows at once the next time, while a new walk brings it up to date.
 *
 * <p>Not thread-safe: one thread (the client thread) adds to it and reads it.
 */
public final class LibraryIndex {
	/** The chosen folders and the excluded ones an index was built for. */
	public record Scope(List<Path> roots, List<Path> excluded) {
		public Scope {
			roots = List.copyOf(roots);
			excluded = List.copyOf(excluded);
		}

		/** True when {@code path} is under a chosen folder and under no excluded one. */
		boolean covers(Path path) {
			return roots.stream().anyMatch(path::startsWith) && excluded.stream().noneMatch(path::startsWith);
		}
	}

	/** The most entries kept; a walk stops there and the index is then {@link #truncated()}. */
	public static final int LIMIT = 50_000;

	private static final String HEADER = "ravecraft-library-index 1";
	private static final Comparator<SourceNode> BY_NAME =
			Comparator.comparing(SourceNode::name, String.CASE_INSENSITIVE_ORDER).thenComparing(SourceNode::id);

	private final Scope scope;
	private final List<SourceNode> entries = new ArrayList<>();
	private final Map<String, Integer> counts = new HashMap<>();
	private boolean sorted = true;
	private List<SourceNode> view = List.of();
	private boolean complete;
	private boolean truncated;
	private int version;

	public LibraryIndex(Scope scope) {
		this.scope = scope;
	}

	public LibraryIndex(List<Path> roots, List<Path> excluded) {
		this(new Scope(roots, excluded));
	}

	public Scope scope() {
		return scope;
	}

	/** Adds a batch found by the walk. */
	public void add(List<SourceNode> batch) {
		for (SourceNode node : batch) {
			entries.add(node);
			if (node.kind() == SourceNode.Kind.TRACK) {
				count(node.id());
			}
		}
		sorted = false;
		version++;
	}

	/** Marks the walk over; {@code truncated} when it stopped at {@link #LIMIT}. */
	public void finish(boolean truncated) {
		complete = true;
		this.truncated = truncated;
		version++;
	}

	/** True once a walk of the whole scope ended; until then the counts are partial. */
	public boolean complete() {
		return complete;
	}

	public boolean truncated() {
		return truncated;
	}

	/** Changes on every change, so a view knows when to rebuild. */
	public int version() {
		return version;
	}

	public int size() {
		return entries.size();
	}

	/** The tracks and playlists sorted by name; the same list is returned until something is added. */
	public List<SourceNode> entries() {
		if (!sorted) {
			entries.sort(BY_NAME);
			view = List.copyOf(entries);
			sorted = true;
		} else if (view.size() != entries.size()) {
			view = List.copyOf(entries);
		}
		return view;
	}

	/** The tracks under the folder {@code id} (its path), subfolders included. */
	public int countUnder(String id) {
		return counts.getOrDefault(key(id), 0);
	}

	/**
	 * Up to {@code max} tracks under the folder {@code id}, subfolders included: a folder's own tracks by name, then
	 * each subfolder's in name order, depth first.
	 */
	public List<Path> tracksUnder(String id, int max) {
		Optional<Path> folder = path(id);
		if (folder.isEmpty() || max <= 0) {
			return List.of();
		}
		List<String> base = parts(folder.get());
		record Found(Path path, List<String> parts) {
		}
		List<Found> found = new ArrayList<>();
		for (SourceNode node : entries) {
			if (node.kind() == SourceNode.Kind.TRACK) {
				path(node.id()).ifPresent(p -> {
					List<String> parts = parts(p);
					if (isUnder(parts, base)) {
						found.add(new Found(p, parts));
					}
				});
			}
		}
		found.sort((a, b) -> IN_FOLDER_ORDER.compare(a.parts(), b.parts()));
		return found.stream().limit(max).map(Found::path).toList();
	}

	private static final Comparator<List<String>> IN_FOLDER_ORDER = (pa, pb) -> {
		int shared = Math.min(pa.size(), pb.size()) - 1;
		for (int i = 0; i < shared; i++) {
			int c = pa.get(i).compareTo(pb.get(i));
			if (c != 0) {
				return c;
			}
		}
		if (pa.size() != pb.size()) {
			// A file sits beside folders: the folder's own files come before its subfolders.
			return Integer.compare(pa.size(), pb.size());
		}
		return pa.get(shared).compareTo(pb.get(shared));
	};

	/** The lowercase name of each part of {@code path}, root first. */
	private static List<String> parts(Path path) {
		List<String> parts = new ArrayList<>();
		if (path.getRoot() != null) {
			parts.add(key(path.getRoot().toString()));
		}
		for (Path part : path) {
			parts.add(key(part.toString()));
		}
		return parts;
	}

	private static boolean isUnder(List<String> track, List<String> folder) {
		return track.size() > folder.size() && track.subList(0, folder.size()).equals(folder);
	}

	/**
	 * A copy holding only what {@code newScope} still covers. It stays complete only for the same scope: a folder
	 * added since was never walked.
	 */
	public LibraryIndex restrictTo(Scope newScope) {
		LibraryIndex copy = new LibraryIndex(newScope);
		List<SourceNode> kept = new ArrayList<>();
		for (SourceNode node : entries) {
			Optional<Path> path = path(node.id());
			if (path.isPresent() && newScope.covers(path.get())) {
				kept.add(node);
			}
		}
		copy.add(kept);
		if (complete && newScope.equals(scope)) {
			copy.finish(truncated);
		}
		return copy;
	}

	/** Saves the index as text: a header, the scope, then one line per entry. */
	public void write(Writer out) throws IOException {
		out.write(HEADER + "\n");
		out.write((complete ? "complete" : "partial") + " " + truncated + "\n");
		for (Path root : scope.roots()) {
			line(out, "R", root.toString());
		}
		for (Path folder : scope.excluded()) {
			line(out, "X", folder.toString());
		}
		for (SourceNode node : entries()) {
			line(out, node.kind() == SourceNode.Kind.PLAYLIST ? "P" : "T", node.id());
		}
	}

	/** Reads an index written by {@link #write}; empty when the text is not one. */
	public static Optional<LibraryIndex> read(Reader in) throws IOException {
		BufferedReader reader = new BufferedReader(in);
		if (!HEADER.equals(reader.readLine())) {
			return Optional.empty();
		}
		String[] state = String.valueOf(reader.readLine()).split(" ");
		if (state.length != 2) {
			return Optional.empty();
		}
		List<Path> roots = new ArrayList<>();
		List<Path> excluded = new ArrayList<>();
		List<SourceNode> nodes = new ArrayList<>();
		for (String line = reader.readLine(); line != null; line = reader.readLine()) {
			if (line.length() < 3 || line.charAt(1) != ' ') {
				continue;
			}
			Optional<Path> path = path(line.substring(2));
			if (path.isEmpty()) {
				continue;
			}
			Path p = path.get();
			switch (line.charAt(0)) {
				case 'R' -> roots.add(p);
				case 'X' -> excluded.add(p);
				case 'T' -> nodes.add(new SourceNode(p.toString(), SourceNode.Kind.TRACK, AudioFiles.baseName(p), -1,
						false));
				case 'P' -> {
					Path name = p.getFileName();
					if (name != null) {
						nodes.add(new SourceNode(p.toString(), SourceNode.Kind.PLAYLIST, name.toString(), -1, false));
					}
				}
				default -> {
				}
			}
		}
		LibraryIndex index = new LibraryIndex(roots, excluded);
		index.add(nodes);
		if (state[0].equals("complete")) {
			index.finish(Boolean.parseBoolean(state[1]));
		}
		return Optional.of(index);
	}

	private void count(String id) {
		Optional<Path> path = path(id);
		if (path.isEmpty()) {
			return;
		}
		for (Path folder = path.get().getParent(); folder != null; folder = folder.getParent()) {
			counts.merge(key(folder.toString()), 1, Integer::sum);
		}
	}

	/** Windows paths ignore case; the tree and the walk may spell a folder differently. */
	private static String key(String id) {
		return id.toLowerCase(java.util.Locale.ROOT);
	}

	private static void line(Writer out, String kind, String path) throws IOException {
		if (path.indexOf('\n') < 0 && path.indexOf('\r') < 0) {
			out.write(kind + " " + path + "\n");
		}
	}

	private static Optional<Path> path(String id) {
		try {
			return Optional.of(Path.of(id));
		} catch (InvalidPathException e) {
			return Optional.empty();
		}
	}
}
