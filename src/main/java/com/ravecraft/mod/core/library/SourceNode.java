package com.ravecraft.mod.core.library;

/** One line of a music source tree. {@code id} is opaque to the screen (the absolute path for the local source). */
public record SourceNode(String id, Kind kind, String name, boolean missing) {
	/** What the node is. */
	public enum Kind {
		FOLDER, PLAYLIST, TRACK
	}

	/** A listed entry as a node: a drive or folder becomes a folder. */
	public static SourceNode of(LibraryEntry entry) {
		Kind kind = switch (entry.kind()) {
			case ROOT, FOLDER -> Kind.FOLDER;
			case PLAYLIST -> Kind.PLAYLIST;
			case TRACK -> Kind.TRACK;
		};
		return new SourceNode(entry.path().toString(), kind, entry.name(), false);
	}

	/** A folder known only by its id, to list its children again. */
	public static SourceNode folder(String id) {
		return new SourceNode(id, Kind.FOLDER, "", false);
	}
}
