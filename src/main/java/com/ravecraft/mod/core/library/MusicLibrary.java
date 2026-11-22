package com.ravecraft.mod.core.library;

import com.ravecraft.mod.core.library.LibraryEntry.Kind;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Browses the player's own files: drives, folders, playlists and tracks. Never recursive; callers run it off the
 * render thread because a folder on a slow drive can take a while to list.
 */
public final class MusicLibrary {
	/** Upper bound on the entries shown for one folder, so a huge folder cannot stall the screen. */
	public static final int MAX_ENTRIES = 2000;

	private static final Comparator<LibraryEntry> BY_NAME =
			Comparator.comparing(LibraryEntry::name, String.CASE_INSENSITIVE_ORDER);

	private MusicLibrary() {
	}

	/**
	 * Drives (or the filesystem root), then the user's home folder and its Music folder when they exist.
	 *
	 * @return the root entries
	 */
	public static List<LibraryEntry> roots() {
		List<LibraryEntry> roots = new ArrayList<>();
		for (File root : File.listRoots()) {
			roots.add(new LibraryEntry(root.toPath(), Kind.ROOT, root.getPath()));
		}
		Path home = Path.of(System.getProperty("user.home"));
		for (Path folder : List.of(home, home.resolve("Music"))) {
			if (Files.isDirectory(folder)) {
				roots.add(new LibraryEntry(folder, Kind.ROOT, folder.toString()));
			}
		}
		return roots;
	}

	/**
	 * Folders first, then playlists and tracks, each sorted by name; hidden and unrelated files are skipped.
	 *
	 * @param folder the folder to list
	 * @return the entries, sorted
	 */
	public static List<LibraryEntry> list(Path folder) throws IOException {
		List<LibraryEntry> folders = new ArrayList<>();
		List<LibraryEntry> files = new ArrayList<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
			for (Path path : stream) {
				if (folders.size() + files.size() >= MAX_ENTRIES) {
					break;
				}
				LibraryEntry entry = toEntry(path);
				if (entry != null) {
					(entry.kind() == Kind.FOLDER ? folders : files).add(entry);
				}
			}
		}
		folders.sort(BY_NAME);
		files.sort(Comparator.comparing((LibraryEntry e) -> e.kind() == Kind.TRACK).thenComparing(BY_NAME));
		List<LibraryEntry> entries = new ArrayList<>(folders);
		entries.addAll(files);
		return entries;
	}

	/**
	 * The drives and home folders of {@link #roots()} as folder nodes, for a folder tree.
	 *
	 * @return the root nodes
	 */
	public static List<SourceNode> rootNodes() {
		return roots().stream().map(SourceNode::of).toList();
	}

	/**
	 * The subfolders of {@code folder} as folder nodes sorted by name, for a folder tree.
	 *
	 * @param folder the folder to list
	 * @return the subfolder nodes, sorted
	 */
	public static List<SourceNode> folderNodes(Path folder) throws IOException {
		return list(folder).stream().filter(LibraryEntry::isBrowsable).map(SourceNode::of).toList();
	}

	/**
	 * The tracks an entry stands for: a track itself, a folder's direct tracks, or a playlist's existing tracks.
	 *
	 * @param entry the library entry
	 * @return the tracks
	 */
	public static List<Path> tracksOf(LibraryEntry entry) throws IOException {
		return switch (entry.kind()) {
			case TRACK -> List.of(entry.path());
			case ROOT, FOLDER -> list(entry.path()).stream()
					.filter(e -> e.kind() == Kind.TRACK)
					.map(LibraryEntry::path)
					.toList();
			case PLAYLIST -> M3uParser.read(entry.path()).stream()
					.filter(p -> AudioFiles.isAudio(p) && Files.isRegularFile(p))
					.toList();
		};
	}

	/** The browser line of {@code path}; null for hidden files and files that are neither audio nor playlists. */
	private static LibraryEntry toEntry(Path path) {
		Path fileName = path.getFileName();
		if (fileName == null || isHidden(path)) {
			return null;
		}
		Kind kind = kindOf(path);
		if (kind == null) {
			return null;
		}
		return new LibraryEntry(path, kind, kind == Kind.TRACK ? AudioFiles.baseName(path) : fileName.toString());
	}

	/** What {@code path} is in the browser; null for a file that is neither audio nor a playlist. */
	private static @Nullable Kind kindOf(Path path) {
		if (Files.isDirectory(path)) {
			return Kind.FOLDER;
		}
		if (AudioFiles.isPlaylist(path)) {
			return Kind.PLAYLIST;
		}
		return AudioFiles.isAudio(path) ? Kind.TRACK : null;
	}

	private static boolean isHidden(Path path) {
		if (path.getFileName().toString().startsWith(".")) {
			return true;
		}
		try {
			return Files.isHidden(path);
		} catch (IOException | SecurityException e) {
			return true;
		}
	}
}
