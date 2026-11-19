package com.ravecraft.mod.core.library;

import java.nio.file.Path;
import java.util.List;

/** A source that is not there yet (USB stick, streaming): shown as a locked tab, with nothing in it. */
public record UnavailableSource(String id) implements MusicSource {
	@Override
	public boolean available() {
		return false;
	}

	@Override
	public List<SourceNode> roots() {
		return List.of();
	}

	@Override
	public List<SourceNode> children(SourceNode folder) {
		return List.of();
	}

	@Override
	public List<Path> tracksOf(SourceNode node) {
		return List.of();
	}

	@Override
	public List<SourceNode> allTracks() {
		return List.of();
	}

	@Override
	public String parentName(SourceNode node) {
		return "";
	}
}
