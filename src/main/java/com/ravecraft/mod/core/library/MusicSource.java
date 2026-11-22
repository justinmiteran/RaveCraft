package com.ravecraft.mod.core.library;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Where the library screen gets its tree from. Every method may block: callers run them off the render thread. */
public interface MusicSource {
	/**
	 * A stable name for the source.
	 *
	 * @return the source's id
	 */
	String id();

	/**
	 * Whether the source can be browsed now.
	 *
	 * @return true when the source can be browsed
	 */
	boolean available();

	/**
	 * The top-level nodes.
	 *
	 * @return the root nodes
	 */
	List<SourceNode> roots();

	/**
	 * The nodes inside {@code folder}.
	 *
	 * @param folder the folder node
	 * @return the child nodes
	 */
	List<SourceNode> children(SourceNode folder) throws IOException;

	/**
	 * The tracks {@code node} stands for: itself, a playlist's entries or a folder's tracks.
	 *
	 * @param node the node
	 * @return the tracks
	 */
	List<Path> tracksOf(SourceNode node) throws IOException;

	/**
	 * A new walk of every track and playlist under the roots, up to {@link LibraryIndex#LIMIT}, for the index.
	 *
	 * @return the new walk
	 */
	FlatScan flatScan();

	/**
	 * What the walk covers, saved with the index; an index of another scope is narrowed down before it is shown.
	 *
	 * @return the scope of the walk
	 */
	default LibraryIndex.Scope indexScope() {
		return new LibraryIndex.Scope(java.util.List.of(), java.util.List.of());
	}

	/**
	 * The name of the folder holding {@code node}, or an empty string when there is none to show.
	 *
	 * @param node the node
	 * @return the parent folder's name, or an empty string
	 */
	String parentName(SourceNode node);
}
