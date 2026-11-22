package com.ravecraft.mod.core.library;

import com.ravecraft.mod.core.library.LibraryEntry.Kind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** The player's own folders on disk, limited to the roots they chose, without the folders they excluded. */
public final class LocalFolderSource implements MusicSource {
	private final Supplier<List<Path>> roots;
	private final Supplier<List<Path>> excluded;

	/**
	 * A source over the folders {@code roots} gives, excluding nothing.
	 *
	 * @param roots gives the chosen folders
	 */
	public LocalFolderSource(Supplier<List<Path>> roots) {
		this(roots, List::of);
	}

	/**
	 * {@code excluded}: folders hidden from the tree and the flat view, with everything under them.
	 *
	 * @param roots gives the chosen folders
	 * @param excluded gives the excluded folders
	 */
	public LocalFolderSource(Supplier<List<Path>> roots, Supplier<List<Path>> excluded) {
		this.roots = roots;
		this.excluded = excluded;
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
			nodes.add(new SourceNode(root.toString(), SourceNode.Kind.FOLDER, name, !Files.isDirectory(root)));
		}
		return nodes;
	}

	@Override
	public List<SourceNode> children(SourceNode folder) throws IOException {
		Path path = Path.of(folder.id());
		if (!Files.isDirectory(path)) {
			return List.of();
		}
		List<LibraryEntry> entries;
		try {
			entries = MusicLibrary.list(path);
		} catch (IOException e) {
			return List.of();
		}
		Exclusions hidden = new Exclusions(excluded.get());
		List<SourceNode> nodes = new ArrayList<>();
		for (LibraryEntry entry : entries) {
			if (!entry.isBrowsable() || !hidden.hides(entry.path())) {
				nodes.add(SourceNode.of(entry));
			}
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
	public FlatScan flatScan() {
		return new FlatScan(roots.get(), excluded.get(), LibraryIndex.LIMIT);
	}

	@Override
	public LibraryIndex.Scope indexScope() {
		return new LibraryIndex.Scope(roots.get(), excluded.get());
	}

	@Override
	public String parentName(SourceNode node) {
		Path parent = Path.of(node.id()).getParent();
		Path name = parent == null ? null : parent.getFileName();
		return name == null ? "" : name.toString();
	}
}
