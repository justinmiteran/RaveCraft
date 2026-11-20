package com.ravecraft.mod.client.library;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.core.io.AtomicFiles;
import com.ravecraft.mod.core.library.FlatScan;
import com.ravecraft.mod.core.library.LibraryIndex;
import com.ravecraft.mod.core.library.MusicSource;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

/**
 * The library index of each music source, kept for the game session and saved in the config directory, so the
 * library shows at once even after a restart. Opening the library shows the saved index and walks the disk again
 * in the background; the walk's result replaces the shown index only once it is complete, except the very first
 * time, when the list fills in as the walk goes. Client thread only, apart from the disk work it starts.
 */
public final class LibraryIndexStore {
	/** Opening the library again within this delay does not walk the disk again; Refresh always does. */
	private static final long FRESH_MILLIS = 60_000L;
	private static final int BATCH = 256;

	private static final class State {
		LibraryIndex shown;
		boolean loaded;
		boolean loading;
		FlatScan scan;
		long lastScan;
	}

	private static final Map<String, State> STATES = new HashMap<>();

	private LibraryIndexStore() {
	}

	/** The index to show for {@code source} now; loads the saved one and refreshes it when due. */
	public static LibraryIndex open(MusicSource source) {
		State state = state(source);
		if (!state.loaded) {
			if (!state.loading) {
				state.loading = true;
				load(source, state);
			}
		} else if (!state.shown.scope().equals(source.indexScope())) {
			refresh(source);
		} else if (state.scan == null && System.currentTimeMillis() - state.lastScan > FRESH_MILLIS) {
			refresh(source);
		}
		return state.shown;
	}

	/** The index shown for {@code source}; it changes object when a walk completes. */
	public static LibraryIndex get(MusicSource source) {
		return state(source).shown;
	}

	/**
	 * The tracks under a folder (subfolders included) as the label shown beside it: {@code …} while the index has
	 * not seen it yet, empty when the source's index was never opened.
	 */
	public static Optional<String> countLabel(String sourceId, String folderId) {
		State state = STATES.get(sourceId);
		if (state == null) {
			return Optional.empty();
		}
		int count = state.shown.countUnder(folderId);
		return Optional.of(count == 0 && !state.shown.complete() ? "…" : Integer.toString(count));
	}

	/** True while the disk is being walked for {@code source}. */
	public static boolean refreshing(MusicSource source) {
		State state = state(source);
		return state.loading || state.scan != null;
	}

	/**
	 * Walks the disk again, keeping the shown index until the walk is done. A walk already running for the same
	 * scope goes on; one for an older scope is stopped. Folders no longer chosen disappear at once.
	 */
	public static void refresh(MusicSource source) {
		State state = state(source);
		if (!state.loaded) {
			open(source);
			return;
		}
		LibraryIndex.Scope scope = source.indexScope();
		if (!state.shown.scope().equals(scope)) {
			state.shown = state.shown.restrictTo(scope);
		}
		if (state.scan != null) {
			state.scan.cancel();
			state.scan = null;
		}
		if (scope.roots().isEmpty()) {
			state.shown = new LibraryIndex(scope);
			state.shown.finish(false);
			return;
		}
		LibraryIndex building = new LibraryIndex(scope);
		if (state.shown.size() == 0 && !state.shown.complete()) {
			// Nothing to show meanwhile: the list fills in as the walk goes.
			state.shown = building;
		}
		FlatScan scan = source.flatScan();
		state.scan = scan;
		Minecraft minecraft = Minecraft.getInstance();
		CompletableFuture.runAsync(() -> {
			boolean more;
			try {
				more = scan.run(batch -> minecraft.execute(() -> {
					if (state.scan == scan) {
						building.add(batch);
					}
				}), BATCH);
			} catch (RuntimeException e) {
				RaveCraft.LOGGER.warn("Could not walk the music library", e);
				more = false;
			}
			boolean truncated = more;
			minecraft.execute(() -> {
				if (state.scan != scan) {
					return;
				}
				state.scan = null;
				state.lastScan = System.currentTimeMillis();
				building.finish(truncated);
				state.shown = building;
				save(source.id(), building);
			});
		}, Util.ioPool());
	}

	private static State state(MusicSource source) {
		return STATES.computeIfAbsent(source.id(), id -> {
			State state = new State();
			state.shown = new LibraryIndex(source.indexScope());
			return state;
		});
	}

	private static Path file(String sourceId) {
		return FabricLoader.getInstance().getConfigDir().resolve("ravecraft-library-index-" + sourceId + ".txt");
	}

	/** Reads the saved index off thread, shows it narrowed to the current scope, then refreshes it. */
	private static void load(MusicSource source, State state) {
		Path file = file(source.id());
		OffThread.run(() -> {
			try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				return LibraryIndex.read(in);
			} catch (NoSuchFileException e) {
				return Optional.<LibraryIndex>empty();
			}
		}, saved -> loaded(source, state, saved), () -> loaded(source, state, Optional.empty()));
	}

	private static void loaded(MusicSource source, State state, Optional<LibraryIndex> saved) {
		state.loading = false;
		state.loaded = true;
		saved.ifPresent(index -> state.shown = index.restrictTo(source.indexScope()));
		refresh(source);
	}

	/** Writes the index off thread, through a temporary file so a crash never leaves half a file. */
	private static void save(String sourceId, LibraryIndex index) {
		index.entries(); // sorted here, on the client thread: the index is not touched again once finished
		Path file = file(sourceId);
		CompletableFuture.runAsync(() -> {
			try {
				AtomicFiles.write(file, index::write);
			} catch (IOException e) {
				RaveCraft.LOGGER.warn("Could not save the music library index", e);
			}
		}, Util.ioPool());
	}
}
