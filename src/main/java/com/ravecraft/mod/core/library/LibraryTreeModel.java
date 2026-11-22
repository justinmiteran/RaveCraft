package com.ravecraft.mod.core.library;

import java.util.ArrayList;
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
 * dropped. In the flat view the rows are the tracks and playlists of the library index instead, replaced whole
 * whenever the index changes.
 * {@link #rows()} is the flattened list of visible rows, rebuilt only after a change.
 */
public final class LibraryTreeModel {
	/** One visible line: a node, or a {@code Loading…} placeholder when {@code node} is null. */
	public record Row(SourceNode node, int depth, boolean expanded) {
		/**
		 * Whether this line is the placeholder of a folder still loading.
		 *
		 * @return true for a placeholder
		 */
		public boolean loading() {
			return node == null;
		}
	}

	private static final Row ROOTS_LOADING = new Row(null, 0, false);

	private int generation;
	private boolean loadingRoots;
	private List<SourceNode> roots = List.of();
	private final Map<String, List<SourceNode>> children = new HashMap<>();
	private final Set<String> expanded = new HashSet<>();
	private final Set<String> loading = new HashSet<>();
	private String filter = "";
	private boolean flat;
	private List<SourceNode> flatNodes = List.of();
	private List<Row> rows = List.of();
	private boolean dirty = true;

	/**
	 * Changes on every {@link #startLoading()}; results from an older generation are ignored.
	 *
	 * @return the generation
	 */
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
		dirty = true;
	}

	/**
	 * Starts listing everything again while the current tree stays shown: results of before are dropped, the new
	 * roots replace the old ones when they arrive. Returns the folders whose children are loaded, to load again.
	 *
	 * @return the ids to load
	 */
	public List<String> refresh() {
		generation++;
		loading.clear();
		List<String> reload = new ArrayList<>(children.keySet());
		reload.removeIf(id -> !expanded.contains(id));
		children.keySet().retainAll(reload);
		loading.addAll(reload);
		return reload;
	}

	/**
	 * Whether the roots are still being listed.
	 *
	 * @return true while the roots load
	 */
	public boolean loadingRoots() {
		return loadingRoots;
	}

	/**
	 * True once the roots arrived and there are none: no folder chosen.
	 *
	 * @return true when there is no folder
	 */
	public boolean isEmpty() {
		return !loadingRoots && roots.isEmpty();
	}

	/**
	 * Applies the roots; false (and nothing changes) when they belong to an older generation.
	 *
	 * @param generation the generation the roots were asked in
	 * @param roots the root nodes
	 * @return true when the roots were applied
	 */
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
	 *
	 * @param folder the folder
	 * @return true when its children must be loaded
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
	 *
	 * @param ids the folders to expand
	 * @return the ids to load
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

	/**
	 * Applies a folder's children; false (and nothing changes) when they belong to an older generation.
	 *
	 * @param generation the generation the children were asked in
	 * @param id the folder's id
	 * @param nodes the children
	 * @return true when the children were applied
	 */
	public boolean setChildren(int generation, String id, List<SourceNode> nodes) {
		if (generation != this.generation) {
			return false;
		}
		loading.remove(id);
		children.put(id, List.copyOf(nodes));
		dirty = true;
		return true;
	}

	/**
	 * Switches between the tree and the flat list; the tree keeps its expanded folders.
	 *
	 * @param flat true for the flat list
	 */
	public void setFlat(boolean flat) {
		if (flat != this.flat) {
			this.flat = flat;
			dirty = true;
		}
	}

	/**
	 * Whether the flat list replaces the tree.
	 *
	 * @return true when the flat list shows
	 */
	public boolean flat() {
		return flat;
	}

	/**
	 * Replaces the flat list with the index's entries, already sorted.
	 *
	 * @param nodes the index's entries, sorted
	 */
	public void setFlatNodes(List<SourceNode> nodes) {
		flatNodes = nodes;
		dirty = true;
	}

	/**
	 * Tracks and playlists in the flat list, before the search filter.
	 *
	 * @return the number of entries in the flat list
	 */
	public int flatCount() {
		return flatNodes.size();
	}

	/**
	 * Keeps only the loaded nodes whose name contains {@code filter} (trimmed, case-insensitive), with their
	 * ancestors. Children loaded under a collapsed folder are searched too; nothing new is loaded.
	 *
	 * @param filter the text the names must contain
	 */
	public void setFilter(String filter) {
		String normalised = filter.trim().toLowerCase(Locale.ROOT);
		if (!normalised.equals(this.filter)) {
			this.filter = normalised;
			dirty = true;
		}
	}

	/**
	 * The visible rows, top to bottom. The same list is returned until something changes.
	 *
	 * @return the visible rows
	 */
	public List<Row> rows() {
		if (dirty) {
			rows = List.copyOf(build());
			dirty = false;
		}
		return rows;
	}

	/** The visible rows, built afresh: the filtered flat list, the loading line, or the tree. */
	private List<Row> build() {
		if (flat) {
			return flatRows();
		}
		List<Row> built = new ArrayList<>();
		if (loadingRoots) {
			built.add(ROOTS_LOADING);
		} else {
			for (SourceNode root : roots) {
				if (filter.isEmpty()) {
					addExpanded(built, root, 0);
				} else {
					addMatching(built, root, 0);
				}
			}
		}
		return built;
	}

	/** The flat list's tracks and playlists whose name holds the filter. */
	private List<Row> flatRows() {
		List<Row> built = new ArrayList<>();
		for (SourceNode node : flatNodes) {
			if (node.name().toLowerCase(Locale.ROOT).contains(filter)) {
				built.add(new Row(node, 0, false));
			}
		}
		return built;
	}

	/** Adds the line of {@code node}, then, when it is expanded, its children or their placeholder. */
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
