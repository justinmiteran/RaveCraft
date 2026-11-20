package com.ravecraft.mod.client.audio.render;

import com.ravecraft.mod.core.acoustics.debug.view.PathView;
import com.ravecraft.mod.core.acoustics.debug.view.RoomView;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The latest sound view snapshots, published by the schedulers while a layer is on and read by the renderer. Each
 * snapshot is immutable; they are swapped whole. Client thread only (ticks and level rendering both run on it).
 */
public final class SoundViewSnapshots {
	private static final Map<Integer, PathView> PATHS = new LinkedHashMap<>();
	private static final Collection<PathView> PATHS_VIEW = Collections.unmodifiableCollection(PATHS.values());
	private static @Nullable RoomView listenerRoom;
	private static final @Nullable RoomView[] SLOT_ROOMS = new RoomView[2];

	private SoundViewSnapshots() {
	}

	/** The last path traced for each audible speaker, loudest first. */
	public static Collection<PathView> paths() {
		return PATHS_VIEW;
	}

	/** The last room measured around the listener, or null. */
	public static @Nullable RoomView listenerRoom() {
		return listenerRoom;
	}

	/** The last room measured around the speaker of reverb slot {@code slot} (0 or 1), or null. */
	public static @Nullable RoomView slotRoom(int slot) {
		return SLOT_ROOMS[slot];
	}

	static void putPath(PathView view) {
		PATHS.put(view.sessionId(), view);
	}

	/** Forgets the paths of the speakers no longer audible, and orders the rest as {@code audibleIds} (loudest first). */
	static void retainPaths(List<Integer> audibleIds) {
		if (PATHS.isEmpty()) {
			return;
		}
		Map<Integer, PathView> kept = new LinkedHashMap<>();
		for (Integer id : audibleIds) {
			PathView view = PATHS.get(id);
			if (view != null) {
				kept.put(id, view);
			}
		}
		PATHS.clear();
		PATHS.putAll(kept);
	}

	static void clearPaths() {
		PATHS.clear();
	}

	static void listenerRoom(@Nullable RoomView view) {
		listenerRoom = view;
	}

	static void slotRoom(int slot, @Nullable RoomView view) {
		SLOT_ROOMS[slot] = view;
	}

	static void clearRooms() {
		listenerRoom = null;
		SLOT_ROOMS[0] = null;
		SLOT_ROOMS[1] = null;
	}
}
