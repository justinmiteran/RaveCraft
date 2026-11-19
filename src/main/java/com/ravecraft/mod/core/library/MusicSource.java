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
}
