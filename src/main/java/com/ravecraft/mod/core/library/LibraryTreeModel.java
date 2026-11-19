package com.ravecraft.mod.core.library;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The state of a library tree, without any I/O: the roots, the children loaded so far, which folders are expanded
 * or loading, and the search filter. The caller loads roots and children itself (off the render thread) and hands
 * them back with the {@link #generation()} it started from, so results arriving after {@link #startLoading()} are
 * dropped. In the flat view the rows are the tracks and playlists a scan found so far instead, added batch by
 * batch.
 * {@link #rows()} is the flattened list of visible rows, rebuilt only after a change.
 */
public final class LibraryTreeModel {
	/** One visible line: a node, or a {@code Loading…} placeholder when {@code node} is null. */
	public record Row(SourceNode node, int depth, boolean expanded) {
		public boolean loading() {
			return node == null;
		}
	}

	private static final Row ROOTS_LOADING = new Row(null, 0, false);
	private static final Comparator<SourceNode> BY_NAME =
			Comparator.comparing(SourceNode::name, String.CASE_INSENSITIVE_ORDER).thenComparing(SourceNode::id);

	private int generation;
	private boolean loadingRoots;
	private List<SourceNode> roots = List.of();
	private final Map<String, List<SourceNode>> children = new HashMap<>();
	private final Set<String> expanded = new HashSet<>();
	private final Set<String> loading = new HashSet<>();
	private String filter = "";
	/** Where the flat list's scan stands: not started, running, paused at a full page, or finished. */
	public enum FlatStatus {
		IDLE, SCANNING, MORE, DONE
	}

	private boolean flat;
	private int flatGeneration;
	private FlatStatus flatStatus = FlatStatus.IDLE;
	private final List<SourceNode> flatNodes = new ArrayList<>();
	private List<Row> rows = List.of();
	private boolean dirty = true;

	/** Changes on every {@link #startLoading()}; results from an older generation are ignored. */
	public int generation() {
		return generation;
	}

	/** Forgets everything and waits for new roots. */
	public void startLoading() {
		generation++;
		loadingRoots = true;
		roots = List.of();
		children.clear();
		expanded.clear();
		loading.clear();
		resetFlat();
		dirty = true;
	}

	public boolean loadingRoots() {
		return loadingRoots;
	}

	/** True once the roots arrived and there are none: no folder chosen. */
	public boolean isEmpty() {
		return !loadingRoots && roots.isEmpty();
	}

	/** Applies the roots; false (and nothing changes) when they belong to an older generation. */
	public boolean setRoots(int generation, List<SourceNode> roots) {
		if (generation != this.generation) {
			return false;
		}
		this.roots = List.copyOf(roots);
		loadingRoots = false;
		dirty = true;
		return true;
	}

	/**
	 * Expands or collapses a folder. Returns true when the folder was just expanded and its children must now be
	 * loaded (then handed back with {@link #setChildren}). Tracks, playlists and missing folders do not expand.
	 */
	public boolean toggle(SourceNode folder) {
		if (folder.kind() != SourceNode.Kind.FOLDER || folder.missing()) {
			return false;
		}
		dirty = true;
		if (expanded.remove(folder.id())) {
			return false;
		}
		expanded.add(folder.id());
		if (children.containsKey(folder.id()) || loading.contains(folder.id())) {
			return false;
		}
		loading.add(folder.id());
		return true;
	}

	/**
	 * Expands every folder of {@code ids} (open ones stay open). Returns the ids whose children must now be loaded,
	 * then handed back with {@link #setChildren}.
	 */
	public List<String> expand(List<String> ids) {
		List<String> toLoad = new ArrayList<>();
		for (String id : ids) {
			expanded.add(id);
			if (!children.containsKey(id) && loading.add(id)) {
				toLoad.add(id);
			}
		}
		dirty = true;
		return toLoad;
	}

	/** Applies a folder's children; false (and nothing changes) when they belong to an older generation. */
	public boolean setChildren(int generation, String id, List<SourceNode> nodes) {
		if (generation != this.generation) {
			return false;
		}
		loading.remove(id);
		children.put(id, List.copyOf(nodes));
		dirty = true;
		return true;
	}

	/** Switches between the tree and the flat list of every track; the tree keeps its expanded folders. */
	public void setFlat(boolean flat) {
		if (flat != this.flat) {
			this.flat = flat;
			if (!flat) {
				// Leaving the flat view stops its scan; coming back starts a new one.
				resetFlat();
			}
			dirty = true;
		}
	}

	public boolean flat() {
		return flat;
	}

	/** Changes whenever the flat list is dropped; batches from an older flat generation are ignored. */
	public int flatGeneration() {
		return flatGeneration;
	}

	public FlatStatus flatStatus() {
		return flatStatus;
	}

	/** Tracks and playlists found so far, before the search filter. */
	public int flatCount() {
		return flatNodes.size();
	}

	/** True once when the flat view is on and its scan has not started: the caller then starts it. */
	public boolean claimFlatLoad() {
		if (!flat || flatStatus != FlatStatus.IDLE) {
			return false;
		}
		flatStatus = FlatStatus.SCANNING;
		return true;
	}

	/** True once when the scan paused at a full page: the caller then runs its next page. */
	public boolean claimLoadMore() {
		if (!flat || flatStatus != FlatStatus.MORE) {
			return false;
		}
		flatStatus = FlatStatus.SCANNING;
		return true;
	}

	/** Adds a batch found by the scan, kept sorted by name; false when it belongs to an older flat generation. */
	public boolean addFlatNodes(int flatGeneration, List<SourceNode> nodes) {
		if (flatGeneration != this.flatGeneration || flatStatus != FlatStatus.SCANNING) {
			return false;
		}
		flatNodes.addAll(nodes);
		flatNodes.sort(BY_NAME);
		dirty = true;
		return true;
	}

	/** Ends a scan run: paused at a full page when {@code more}, finished otherwise. */
	public boolean finishFlat(int flatGeneration, boolean more) {
		if (flatGeneration != this.flatGeneration || flatStatus != FlatStatus.SCANNING) {
			return false;
		}
		flatStatus = more ? FlatStatus.MORE : FlatStatus.DONE;
		return true;
	}

	private void resetFlat() {
		flatGeneration++;
		flatStatus = FlatStatus.IDLE;
		flatNodes.clear();
		dirty = true;
	}

	/**
	 * Keeps only the loaded nodes whose name contains {@code filter} (trimmed, case-insensitive), with their
	 * ancestors. Children loaded under a collapsed folder are searched too; nothing new is loaded.
	 */
	public void setFilter(String filter) {
		String normalised = filter.trim().toLowerCase(Locale.ROOT);
		if (!normalised.equals(this.filter)) {
			this.filter = normalised;
			dirty = true;
		}
	}

	/** The visible rows, top to bottom. The same list is returned until something changes. */
	public List<Row> rows() {
		if (dirty) {
			List<Row> built = new ArrayList<>();
			if (flat) {
				for (SourceNode node : flatNodes) {
					if (node.name().toLowerCase(Locale.ROOT).contains(filter)) {
						built.add(new Row(node, 0, false));
					}
				}
			} else if (loadingRoots) {
				built.add(ROOTS_LOADING);
			} else if (filter.isEmpty()) {
				for (SourceNode root : roots) {
					addExpanded(built, root, 0);
				}
			} else {
				for (SourceNode root : roots) {
					addMatching(built, root, 0);
				}
			}
			rows = List.copyOf(built);
			dirty = false;
		}
		return rows;
	}

	private void addExpanded(List<Row> out, SourceNode node, int depth) {
		boolean open = expanded.contains(node.id());
		out.add(new Row(node, depth, open));
		if (!open) {
			return;
		}
		List<SourceNode> loaded = children.get(node.id());
		if (loaded == null) {
			out.add(new Row(null, depth + 1, false));
			return;
		}
		for (SourceNode child : loaded) {
			addExpanded(out, child, depth + 1);
		}
	}

	/** Adds {@code node} and its matching descendants when any of them matches; returns whether it added rows. */
	private boolean addMatching(List<Row> out, SourceNode node, int depth) {
		int at = out.size();
		for (SourceNode child : children.getOrDefault(node.id(), List.of())) {
			addMatching(out, child, depth + 1);
		}
		if (out.size() > at) {
			// Some descendants matched: the node goes above them, shown open.
			out.add(at, new Row(node, depth, true));
			return true;
		}
		if (node.name().toLowerCase(Locale.ROOT).contains(filter)) {
			out.add(new Row(node, depth, false));
			return true;
		}
		return false;
	}
}
