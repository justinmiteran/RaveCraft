package com.ravecraft.mod.core.library;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Where the library screen gets its tree from. Every method may block: callers run them off the render thread. */
public interface MusicSource {
	String id();

	boolean available();

	List<SourceNode> roots();

	List<SourceNode> children(SourceNode folder) throws IOException;

	List<Path> tracksOf(SourceNode node) throws IOException;

	/** A new walk of every track and playlist under the roots, up to {@link LibraryIndex#LIMIT}, for the index. */
	FlatScan flatScan();

	/** What the walk covers, saved with the index; an index of another scope is narrowed down before it is shown. */
	default LibraryIndex.Scope indexScope() {
		return new LibraryIndex.Scope(java.util.List.of(), java.util.List.of());
	}

	/** The name of the folder holding {@code node}, or an empty string when there is none to show. */
	String parentName(SourceNode node);
}
