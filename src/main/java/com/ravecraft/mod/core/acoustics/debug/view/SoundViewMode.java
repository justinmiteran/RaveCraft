package com.ravecraft.mod.core.acoustics.debug.view;

/** Which layers of the in-world sound view are shown; F3+U goes through them in declaration order. */
public enum SoundViewMode {
	NONE(false, false),
	PATHS(true, false),
	ROOMS(false, true),
	ALL(true, true);

	private final boolean paths;
	private final boolean rooms;

	SoundViewMode(boolean paths, boolean rooms) {
		this.paths = paths;
		this.rooms = rooms;
	}

	/**
	 * The mode showing exactly the given layers.
	 *
	 * @param paths true to show the sound paths
	 * @param rooms true to show the room probes
	 * @return the matching mode
	 */
	public static SoundViewMode of(boolean paths, boolean rooms) {
		return paths ? (rooms ? ALL : PATHS) : (rooms ? ROOMS : NONE);
	}

	/**
	 * The next mode of the cycle: none, paths, rooms, both, none.
	 *
	 * @return the next mode
	 */
	public SoundViewMode next() {
		SoundViewMode[] all = values();
		return all[(ordinal() + 1) % all.length];
	}

	/**
	 * Whether the sound paths (direct path, crossed blocks, detours) are shown.
	 *
	 * @return true when the paths are shown
	 */
	public boolean paths() {
		return paths;
	}

	/**
	 * Whether the room probes (rays, room summary, echo) are shown.
	 *
	 * @return true when the room probes are shown
	 */
	public boolean rooms() {
		return rooms;
	}
}
