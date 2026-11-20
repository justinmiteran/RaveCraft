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
	public static final int ROW_H = 11;
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
		super(0, 0, width, height, ROW_H, Component.translatable(SpeakerSprites.KEY + "folders.title"));
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

	private List<Row> rows() {
		return model.rows();
	}

	@Override
	protected int rowCount() {
		return model.isEmpty() ? 0 : rows().size();
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
		return mouseX >= rowRight() - ACTION_W && mouseX < rowRight();
	}

	private boolean overMark(double mouseX) {
		int x = rowRight() - ACTION_W - MARK_W;
		return mouseX >= x && mouseX < x + MARK_W;
	}

	@Override
	protected boolean clickRow(int index, double mouseX, boolean doubleClick) {
		Row row = rows().get(index);
		if (row.loading()) {
			return false;
		}
		Path path = Path.of(row.node().id());
		if (overAction(mouseX)) {
			if (root(row)) {
				actions.remove(path);
			} else if (excluded(row)) {
				actions.include(path);
			} else {
				actions.exclude(path);
			}
			return true;
		}
		if (marked(row) && overMark(mouseX)) {
			reveal(path);
			return true;
		}
		if (excluded(row)) {
			return false;
		}
		toggle(row.node());
		return !row.node().missing();
	}

	@Override
	protected void drawEmpty(GuiGraphicsExtractor graphics, Font font) {
		drawMessage(graphics, font, "no_folders");
	}

	@Override
	protected void drawRow(GuiGraphicsExtractor graphics, Font font, int index, int left, int right, int y,
			boolean hovered, int mouseX, int mouseY) {
		Row row = rows().get(index);
		int x = indent(row, left);
		if (row.loading()) {
			drawLoading(graphics, font, x, right, y);
			return;
		}
		SourceNode node = row.node();
		boolean excluded = excluded(row);
		boolean missing = node.missing();
		boolean grey = excluded || missing;
		if (hovered) {
			graphics.fill(left, y, right, y + ROW_H, HOVER);
		}
		if (!grey) {
			SpeakerSprites.drawArrow(graphics, x + 1, y + 3, row.expanded());
		}
		int iconX = x + ARROW_W;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SpeakerSprites.FOLDER, iconX, y + 1, SpeakerSprites.ICON,
				SpeakerSprites.ICON, grey ? 0.4F : 1.0F);

		int actionX = right - ACTION_W;
		boolean overAction = hovered && overAction(mouseX);
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
			if (overAction) {
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
			String count = missing ? Component.translatable(SpeakerSprites.KEY + "missing").getString()
					: LibraryIndexStore.countLabel("local", node.id()).orElse("…");
			end -= font.width(count);
			graphics.text(font, count, end, y + 2, SpeakerSprites.MUTED, false);
			end -= 3;
		}
		int nameX = iconX + SpeakerSprites.ICON + 2;
		String name = root(row) ? node.id() : node.name();
		graphics.text(font, SpeakerSprites.fit(font, name, end - nameX), nameX, y + 2,
				grey ? SpeakerSprites.ABSENT : SpeakerSprites.TEXT, !grey);
	}
}
