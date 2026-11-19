package com.ravecraft.mod.core.library;

import com.ravecraft.mod.core.library.LibraryEntry.Kind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/** The player's own folders on disk, limited to the roots they chose. */
public final class LocalFolderSource implements MusicSource {
	/** Most entries the flat view lists, and most folders it walks, so a huge disk cannot stall the screen. */
	public static final int FLAT_LIMIT = MusicLibrary.MAX_ENTRIES;
	private static final Comparator<SourceNode> BY_NAME =
			Comparator.comparing(SourceNode::name, String.CASE_INSENSITIVE_ORDER).thenComparing(SourceNode::id);

	private final Supplier<List<Path>> roots;

	public LocalFolderSource(Supplier<List<Path>> roots) {
		this.roots = roots;
	}

	@Override
	public String id() {
		return "local";
	}

	@Override
	public boolean available() {
		return true;
	}

	@Override
	public List<SourceNode> roots() {
		List<SourceNode> nodes = new ArrayList<>();
		for (Path root : roots.get()) {
			Path fileName = root.getFileName();
			String name = fileName == null ? root.toString() : fileName.toString();
			boolean missing = !Files.isDirectory(root);
			nodes.add(new SourceNode(root.toString(), SourceNode.Kind.FOLDER, name,
					missing ? 0 : countTracks(root), missing));
		}
		return nodes;
	}

	@Override
	public List<SourceNode> children(SourceNode folder) throws IOException {
		Path path = Path.of(folder.id());
		if (!Files.isDirectory(path)) {
			return List.of();
		}
		List<SourceNode> nodes = new ArrayList<>();
		List<LibraryEntry> entries;
		try {
			entries = MusicLibrary.list(path);
		} catch (IOException e) {
			return List.of();
		}
		for (LibraryEntry entry : entries) {
			nodes.add(switch (entry.kind()) {
				case ROOT, FOLDER -> new SourceNode(entry.path().toString(), SourceNode.Kind.FOLDER, entry.name(),
						countTracks(entry.path()), false);
				case PLAYLIST -> new SourceNode(entry.path().toString(), SourceNode.Kind.PLAYLIST, entry.name(),
						-1, false);
				case TRACK -> new SourceNode(entry.path().toString(), SourceNode.Kind.TRACK, entry.name(), -1, false);
			});
		}
		return nodes;
	}

	@Override
	public List<Path> tracksOf(SourceNode node) throws IOException {
		Path path = Path.of(node.id());
		return switch (node.kind()) {
			case TRACK -> List.of(path);
			case FOLDER -> Files.isDirectory(path)
					? MusicLibrary.tracksOf(new LibraryEntry(path, Kind.FOLDER, node.name()))
					: List.of();
			case PLAYLIST -> Files.isRegularFile(path)
					? MusicLibrary.tracksOf(new LibraryEntry(path, Kind.PLAYLIST, node.name()))
					: List.of();
		};
	}

	@Override
	public List<SourceNode> allTracks() {
		return allTracks(FLAT_LIMIT);
	}

	/**
	 * Walks the roots breadth first, each folder once (nested roots and links back up are not walked twice),
	 * keeping at most {@code limit} tracks and playlists. Missing roots and unreadable folders are skipped.
	 */
	List<SourceNode> allTracks(int limit) {
		List<SourceNode> nodes = new ArrayList<>();
		Set<Path> seen = new HashSet<>();
		Deque<Path> pending = new ArrayDeque<>(roots.get());
		int walked = 0;
		while (!pending.isEmpty() && nodes.size() < limit && walked < FLAT_LIMIT) {
			Path folder = pending.poll();
			if (!Files.isDirectory(folder) || !seen.add(realPath(folder))) {
				continue;
			}
			walked++;
			List<LibraryEntry> entries;
			try {
				entries = MusicLibrary.list(folder);
			} catch (IOException e) {
				continue;
			}
			for (LibraryEntry entry : entries) {
				switch (entry.kind()) {
					case ROOT, FOLDER -> pending.add(entry.path());
					case PLAYLIST, TRACK -> {
						if (nodes.size() < limit) {
							nodes.add(fileNode(entry));
						}
					}
				}
			}
		}
		nodes.sort(BY_NAME);
		return nodes;
	}

	@Override
	public String parentName(SourceNode node) {
		Path parent = Path.of(node.id()).getParent();
		Path name = parent == null ? null : parent.getFileName();
		return name == null ? "" : name.toString();
	}

	private static SourceNode fileNode(LibraryEntry entry) {
		SourceNode.Kind kind = entry.kind() == Kind.PLAYLIST ? SourceNode.Kind.PLAYLIST : SourceNode.Kind.TRACK;
		return new SourceNode(entry.path().toString(), kind, entry.name(), -1, false);
	}

	private static Path realPath(Path folder) {
		try {
			return folder.toRealPath();
		} catch (IOException e) {
			return folder.toAbsolutePath().normalize();
		}
	}

	/** Direct audio files only; an unreadable folder counts as empty. */
	private static int countTracks(Path folder) {
		try {
			int count = 0;
			for (LibraryEntry entry : MusicLibrary.list(folder)) {
				if (entry.kind() == Kind.TRACK) {
					count++;
				}
			}
			return count;
		} catch (IOException e) {
			return 0;
		}
	}
}
