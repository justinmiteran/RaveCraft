package com.ravecraft.mod.client.screen.widget;

import com.ravecraft.mod.client.library.LibraryIndexStore;
import com.ravecraft.mod.core.library.LibraryFolders;
import com.ravecraft.mod.core.library.LibraryTreeModel.Row;
import com.ravecraft.mod.core.library.LocalFolderSource;
import com.ravecraft.mod.core.library.MusicLibrary;
import com.ravecraft.mod.core.library.SourceNode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The chosen folders as a tree of their subfolders, in the {@code Folders…} popup. A chosen folder shows its full
 * path, its track count and a ✕ that removes it. Hovering a subfolder shows a no-entry sign that excludes it; an
 * excluded folder is greyed with a red sign that shows it again. A collapsed folder holding an exclusion carries a
 * small red sign: clicking it expands every folder down to the exclusions inside.
 */
public final class FolderTree extends TreeList {
	/** What the tree's items do. */
	public interface Actions {
		/**
		 * Removes the chosen folder {@code chosen}.
		 *
		 * @param chosen the chosen folder
		 */
		void remove(Path chosen);

		/**
		 * Excludes {@code folder} from the library.
		 *
		 * @param folder the folder to exclude
		 */
		void exclude(Path folder);

		/**
		 * Shows the excluded {@code folder} again.
		 *
		 * @param folder the excluded folder
		 */
		void include(Path folder);
	}

	private final Actions actions;
	private LibraryFolders folders = LibraryFolders.empty();
	/** The chosen folders the roots are listed from. */
	private volatile List<Path> roots = List.of();

	/**
	 * An empty tree; {@code actions} does what its items ask.
	 *
	 * @param width the tree's width
	 * @param height the tree's height
	 * @param actions does what the items ask
	 */
	public FolderTree(int width, int height, Actions actions) {
		super(0, 0, width, height, Component.translatable(SpeakerSprites.KEY + "folders.title"));
		this.actions = actions;
	}

	/**
	 * Shows {@code folders}: the tree is reloaded when the chosen folders changed, kept as it is otherwise.
	 *
	 * @param folders the chosen folders
	 */
	public void setFolders(LibraryFolders folders) {
		boolean reload = model.generation() == 0 || !folders.paths().equals(this.folders.paths());
		this.folders = folders;
		if (reload) {
			roots = folders.paths();
			loadRoots();
		}
	}

	@Override
	protected List<SourceNode> listRoots() {
		List<Path> chosen = roots;
		return new LocalFolderSource(() -> chosen).roots();
	}

	@Override
	protected List<SourceNode> listChildren(String folderId) throws IOException {
		return MusicLibrary.folderNodes(Path.of(folderId));
	}

	private void reveal(Path folder) {
		List<String> ids = new ArrayList<>();
		for (Path path : folders.foldersToReveal(folder)) {
			ids.add(path.toString());
		}
		for (String id : model.expand(ids)) {
			loadChildren(id);
		}
	}

	private static boolean root(Row row) {
		return row.depth() == 0;
	}

	private boolean excluded(Row row) {
		return !root(row) && folders.isExcluded(Path.of(row.node().id()));
	}

	/** A collapsed folder with an exclusion inside: it carries the reveal sign. */
	private boolean marked(Row row) {
		return !row.expanded() && !row.node().missing() && !excluded(row)
				&& folders.holdsExclusion(Path.of(row.node().id()));
	}

	@Override
	protected void drawEmpty(GuiGraphicsExtractor graphics, Font font) {
		drawMessage(graphics, font, "no_folders");
	}

	@Override
	protected LibraryRow rowFor(Row row) {
		SourceNode node = row.node();
		Path path = Path.of(node.id());
		boolean excluded = excluded(row);
		RowItem.Button action;
		if (root(row)) {
			action = RowItem.button(SpeakerSprites.REMOVE, tip("remove"), () -> actions.remove(path));
		} else if (excluded) {
			action = RowItem.button(SpeakerSprites.EXCLUDED, tip("folders.include"), () -> actions.include(path));
		} else {
			action = RowItem.button(SpeakerSprites.EXCLUDE, tip("folders.exclude_hint"), () -> actions.exclude(path))
					.onHover();
		}
		RowItem reveal = marked(row)
				? RowItem.button(SpeakerSprites.EXCLUDED, tip("folders.reveal"), () -> reveal(path))
				: null;
		RowItem count = excluded ? null : RowItem.text(node.missing() ? tip("missing").getString()
				: LibraryIndexStore.countLabel("local", node.id()).orElse("…"));
		return folder(row).item(action).item(reveal).item(count)
				.greyed(excluded || node.missing())
				.label(root(row) ? node.id() : node.name());
	}

	private static Component tip(String key) {
		return Component.translatable(SpeakerSprites.KEY + key);
	}
}
