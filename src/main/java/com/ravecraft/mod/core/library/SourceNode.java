package com.ravecraft.mod.core.library;

/**
 * One line of a music source tree. {@code id} is opaque to the screen (the absolute path for the local source);
 * {@code trackCount} is -1 when it does not apply (playlists, tracks).
 */
public record SourceNode(String id, Kind kind, String name, int trackCount, boolean missing) {
	public enum Kind {
		FOLDER, PLAYLIST, TRACK
	}
}
