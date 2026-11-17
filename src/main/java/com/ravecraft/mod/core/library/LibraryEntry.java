package com.ravecraft.mod.core.library;

import java.nio.file.Path;

/** One line of the music browser: a drive or home folder, a folder, a playlist or a track. */
public record LibraryEntry(Path path, Kind kind, String name) {
	public enum Kind {
		ROOT, FOLDER, PLAYLIST, TRACK
	}

	public boolean isBrowsable() {
		return kind == Kind.ROOT || kind == Kind.FOLDER;
	}
}
