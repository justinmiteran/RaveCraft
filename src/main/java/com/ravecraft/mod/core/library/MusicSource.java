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

	/** A new walk of every track and playlist under the roots, for the flat view. */
	FlatScan flatScan();

	/** The name of the folder holding {@code node}, or an empty string when there is none to show. */
	String parentName(SourceNode node);
}
