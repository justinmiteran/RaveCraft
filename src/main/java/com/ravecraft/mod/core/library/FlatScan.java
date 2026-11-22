package com.ravecraft.mod.core.library;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

/**
 * Walks the chosen folders breadth first for the library index, up to a limit, handing the tracks and playlists it
 * finds to a sink in batches so the list fills in while the walk goes on. Each folder is walked once (nested roots
 * and links back up are skipped), unreadable folders are skipped, and system or software folders met on the way are
 * not entered, nor are the folders the player excluded; a root the player chose is always walked, whatever its name.
 *
 * <p>{@link #run} blocks and is meant for one IO thread; {@link #cancel} may be called from any thread.
 */
public final class FlatScan {
	/** Folder names (lower case) not entered on the way down: system, program and tool folders. */
	public static final Set<String> EXCLUDED = Set.of(
			"windows", "program files", "program files (x86)", "programdata", "appdata", "$recycle.bin",
			"system volume information", "recovery", "perflogs",
			"system", "library", "applications", "volumes", "private",
			"proc", "sys", "dev", "usr", "bin", "sbin", "lib", "lib64", "etc", "boot", "var", "tmp", "snap", "opt",
			"node_modules", "steamapps");
	private static final long FLUSH_NANOS = 100_000_000L;

	/** Lists a folder's entries; {@link MusicLibrary#list} outside tests. */
	interface Lister {
		List<LibraryEntry> list(Path folder) throws IOException;
	}

	private final Deque<Path> pending = new ArrayDeque<>();
	private final Set<Path> seen = new HashSet<>();
	private final Exclusions hidden;
	private final int limit;
	private final Lister lister;
	private final List<SourceNode> batch = new ArrayList<>();
	private int found;
	private long lastFlush;
	private volatile boolean cancelled;

	/**
	 * A walk of {@code roots}, excluding nothing, up to {@link LibraryIndex#LIMIT}.
	 *
	 * @param roots the folders to walk
	 */
	public FlatScan(List<Path> roots) {
		this(roots, List.of(), LibraryIndex.LIMIT);
	}

	/**
	 * {@code excluded}: folders not entered when met on the way, so nothing under them is walked either;
	 * {@code limit}: the walk stops once it found this many tracks and playlists, however many folders that takes.
	 *
	 * @param roots the folders to walk
	 * @param excluded the folders to skip
	 * @param limit the most tracks to find
	 */
	public FlatScan(List<Path> roots, List<Path> excluded, int limit) {
		this(roots, excluded, limit, MusicLibrary::list);
	}

	FlatScan(List<Path> roots, List<Path> excluded, int limit, Lister lister) {
		this.limit = limit;
		this.lister = lister;
		this.hidden = new Exclusions(excluded);
		pending.addAll(roots);
	}

	/**
	 * Walks until the limit is reached, nothing is left or the scan is cancelled. A batch goes to {@code sink} every
	 * {@code batchSize} entries, after a folder when the last batch is 100 ms old, and at the end. Returns true when
	 * it stopped at the limit with folders left to walk.
	 *
	 * @param sink receives each batch
	 * @param batchSize the entries per batch
	 * @return true when the walk reached the limit
	 */
	public boolean run(Consumer<List<SourceNode>> sink, int batchSize) {
		batch.clear();
		lastFlush = System.nanoTime();
		while (!cancelled && !pending.isEmpty() && found < limit) {
			List<LibraryEntry> entries = open(pending.poll());
			if (entries == null) {
				continue;
			}
			takeAll(entries, sink, batchSize);
			if (!batch.isEmpty() && System.nanoTime() - lastFlush >= FLUSH_NANOS) {
				flush(sink);
			}
		}
		if (!batch.isEmpty() && !cancelled) {
			flush(sink);
		}
		return !cancelled && !pending.isEmpty();
	}

	/** The entries of {@code folder}, marking it seen; null for a folder seen already, gone or unreadable. */
	private @Nullable List<LibraryEntry> open(Path folder) {
		Path real = realPath(folder);
		if (!Files.isDirectory(folder) || seen.contains(real)) {
			return null;
		}
		List<LibraryEntry> entries;
		try {
			entries = lister.list(folder);
		} catch (IOException | SecurityException e) {
			// Not marked as seen: a link that cannot be listed must not hide the folder it points to.
			return null;
		}
		seen.add(real);
		return entries;
	}

	/** Takes the entries of one folder in order, until the scan is cancelled. */
	private void takeAll(List<LibraryEntry> entries, Consumer<List<SourceNode>> sink, int batchSize) {
		for (LibraryEntry entry : entries) {
			if (cancelled) {
				return;
			}
			take(entry, sink, batchSize);
		}
	}

	/** Queues a folder to walk, or adds a track or playlist to the batch, flushing a full batch. */
	private void take(LibraryEntry entry, Consumer<List<SourceNode>> sink, int batchSize) {
		switch (entry.kind()) {
			case ROOT, FOLDER -> {
				if (!excluded(entry.path()) && !hidden.hides(entry.path())) {
					pending.add(entry.path());
				}
			}
			case PLAYLIST, TRACK -> {
				batch.add(SourceNode.of(entry));
				found++;
				if (batch.size() >= batchSize) {
					flush(sink);
				}
			}
		}
	}

	/** Stops the walk at its next step; safe from any thread. */
	public void cancel() {
		cancelled = true;
	}

	private void flush(Consumer<List<SourceNode>> sink) {
		sink.accept(List.copyOf(batch));
		batch.clear();
		lastFlush = System.nanoTime();
	}

	private static boolean excluded(Path folder) {
		Path name = folder.getFileName();
		return name != null && EXCLUDED.contains(name.toString().toLowerCase(Locale.ROOT));
	}

	private static Path realPath(Path folder) {
		try {
			return folder.toRealPath();
		} catch (IOException | SecurityException e) {
			return folder.toAbsolutePath().normalize();
		}
	}
}
