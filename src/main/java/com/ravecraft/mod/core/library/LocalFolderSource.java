package com.ravecraft.mod.core.library;

import com.ravecraft.mod.core.library.LibraryEntry.Kind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** The player's own folders on disk, limited to the roots they chose. */
public final class LocalFolderSource implements MusicSource {
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
		for (LibraryEntry entry : MusicLibrary.list(path)) {
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
