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
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The chosen folders as a tree of their subfolders, in the {@code Folders…} popup. A chosen folder shows its full
 * path, its track count and a ✕ that removes it. Hovering a subfolder shows a no-entry sign that excludes it; an
 * excluded folder is greyed with a red sign that shows it again. A collapsed folder holding an exclusion carries a
 * small red sign: clicking it expands every folder down to the exclusions inside.
 */
public final class FolderTree extends TreeList {
	private static final int ACTION_W = SpeakerSprites.ICON + 2;
	private static final int MARK_W = SpeakerSprites.ICON + 2;

	public interface Actions {
		void remove(Path chosen);

		void exclude(Path folder);

		void include(Path folder);
	}

	private final Actions actions;
	private LibraryFolders folders = LibraryFolders.empty();
	/** The chosen folders the roots are listed from. */
	private volatile List<Path> roots = List.of();

	public FolderTree(int width, int height, Actions actions) {
		super(0, 0, width, height, Component.translatable(SpeakerSprites.KEY + "folders.title"));
		this.actions = actions;
	}

	/** Shows {@code folders}: the tree is reloaded when the chosen folders changed, kept as it is otherwise. */
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

	private boolean overAction(double mouseX) {
		return overRight(mouseX, ACTION_W);
	}

	private boolean overMark(double mouseX) {
		int x = rowRight() - ACTION_W - MARK_W;
		return mouseX >= x && mouseX < x + MARK_W;
	}

	@Override
	protected boolean greyed(Row row) {
		return excluded(row) || row.node().missing();
	}

	@Override
	protected String label(Row row) {
		return root(row) ? row.node().id() : row.node().name();
	}

	@Override
	protected boolean overEnd(Row row, double mouseX) {
		return overAction(mouseX) || marked(row) && overMark(mouseX);
	}

	@Override
	protected boolean clickEnd(Row row, double mouseX) {
		Path path = Path.of(row.node().id());
		if (!overAction(mouseX)) {
			reveal(path);
		} else if (root(row)) {
			actions.remove(path);
		} else if (excluded(row)) {
			actions.include(path);
		} else {
			actions.exclude(path);
		}
		return true;
	}

	@Override
	protected void drawEmpty(GuiGraphicsExtractor graphics, Font font) {
		drawMessage(graphics, font, "no_folders");
	}

	@Override
	protected int drawEnd(GuiGraphicsExtractor graphics, Font font, Row row, int right, int y, boolean hovered,
			int mouseX, int mouseY) {
		boolean excluded = excluded(row);
		int actionX = right - ACTION_W;
		Identifier action = null;
		String actionTip = null;
		if (root(row)) {
			action = SpeakerSprites.REMOVE;
			actionTip = "remove";
		} else if (excluded) {
			action = SpeakerSprites.EXCLUDED;
			actionTip = "folders.include";
		} else if (hovered) {
			action = SpeakerSprites.EXCLUDE;
			actionTip = "folders.exclude_hint";
		}
		if (action != null) {
			if (hovered && overAction(mouseX)) {
				graphics.fill(actionX, y, actionX + ACTION_W, y + ROW_H, HOVER);
				graphics.setTooltipForNextFrame(font, Component.translatable(SpeakerSprites.KEY + actionTip), mouseX,
						mouseY);
			}
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, action, actionX + 1, y + 1, SpeakerSprites.ICON,
					SpeakerSprites.ICON);
		}

		int end = actionX - 2;
		if (marked(row)) {
			int markX = actionX - MARK_W;
			if (hovered && overMark(mouseX)) {
				graphics.fill(markX, y, markX + MARK_W, y + ROW_H, HOVER);
				graphics.setTooltipForNextFrame(font, Component.translatable(SpeakerSprites.KEY + "folders.reveal"),
						mouseX, mouseY);
			}
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SpeakerSprites.EXCLUDED, markX + 1, y + 1,
					SpeakerSprites.ICON, SpeakerSprites.ICON);
			end = markX - 1;
		}
		if (!excluded) {
			String count = row.node().missing() ? Component.translatable(SpeakerSprites.KEY + "missing").getString()
					: LibraryIndexStore.countLabel("local", row.node().id()).orElse("…");
			end -= font.width(count);
			graphics.text(font, count, end, y + 2, SpeakerSprites.MUTED, false);
			end -= 3;
		}
		return end;
	}
}
