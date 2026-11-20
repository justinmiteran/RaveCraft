package com.ravecraft.mod.client.screen.widget;

import com.ravecraft.mod.core.library.LibraryTreeModel.Row;
import com.ravecraft.mod.core.library.MusicLibrary;
import com.ravecraft.mod.core.library.SourceNode;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

/**
 * The in-game folder browser, used when the system dialog cannot open: the drives and home folders as an
 * expandable tree of folders, like the library, with a {@code +} on each folder to add it. A folder already
 * covered by a chosen one has its {@code +} greyed out. Listing runs off the render thread.
 */
public final class FolderBrowser extends TreeList {
	private static final int ROW_H = 11;
	private static final int ADD_W = SpeakerSprites.ICON + 1;

	private final Consumer<Path> onAdd;
	private Predicate<Path> covered = path -> false;

	public FolderBrowser(int width, int height, Consumer<Path> onAdd) {
		super(0, 0, width, height, ROW_H, Component.translatable(SpeakerSprites.KEY + "folders.browser"));
		this.onAdd = onAdd;
	}

	/** Which folders already are in the library: their {@code +} is greyed out. */
	public void setCovered(Predicate<Path> covered) {
		this.covered = covered;
	}

	/** Lists the drives and home folders again, everything folded. */
	public void open() {
		loadRoots();
	}

	@Override
	protected List<SourceNode> listRoots() {
		return MusicLibrary.rootNodes();
	}

	@Override
	protected List<SourceNode> listChildren(String folderId) throws IOException {
		return MusicLibrary.folderNodes(Path.of(folderId));
	}

	private static Optional<Path> path(SourceNode node) {
		try {
			return Optional.of(Path.of(node.id()));
		} catch (InvalidPathException e) {
			return Optional.empty();
		}
	}

	private boolean addable(SourceNode node) {
		return path(node).filter(covered.negate()).isPresent();
	}

	private boolean overAdd(double mouseX) {
		return mouseX >= rowRight() - ADD_W && mouseX < rowRight();
	}

	@Override
	protected int rowCount() {
		return model.rows().size();
	}

	@Override
	protected boolean clickRow(int index, double mouseX, boolean doubleClick) {
		Row row = model.rows().get(index);
		if (row.loading()) {
			return false;
		}
		if (overAdd(mouseX)) {
			if (!addable(row.node())) {
				return false;
			}
			onAdd.accept(path(row.node()).orElseThrow());
			return true;
		}
		toggle(row.node());
		return true;
	}

	@Override
	protected void drawRow(GuiGraphicsExtractor graphics, Font font, int index, int left, int right, int y,
			boolean hovered, int mouseX, int mouseY) {
		Row row = model.rows().get(index);
		int x = indent(row, left);
		if (row.loading()) {
			drawLoading(graphics, font, x, right, y);
			return;
		}
		SourceNode node = row.node();
		if (hovered) {
			graphics.fill(left, y, right, y + ROW_H, HOVER);
		}
		SpeakerSprites.drawArrow(graphics, x + 1, y + 3, row.expanded());
		int iconX = x + ARROW_W;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SpeakerSprites.FOLDER, iconX, y + 1, SpeakerSprites.ICON,
				SpeakerSprites.ICON);
		int addX = right - ADD_W;
		boolean addable = addable(node);
		if (hovered && overAdd(mouseX)) {
			if (addable) {
				SpeakerSprites.cellHover(graphics, addX - 1, y, right, y + ROW_H);
			}
			Component tip = Component.translatable(SpeakerSprites.KEY + (addable ? "folders.add_this"
					: "folders.inside_existing"));
			graphics.setTooltipForNextFrame(font, tip, mouseX, mouseY);
		} else if (hovered) {
			graphics.setTooltipForNextFrame(font, Component.literal(node.id()), mouseX, mouseY);
		}
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SpeakerSprites.PLUS, addX, y + 1, SpeakerSprites.ICON,
				SpeakerSprites.ICON, addable ? 1.0F : 0.3F);
		int nameX = iconX + SpeakerSprites.ICON + 2;
		graphics.text(font, SpeakerSprites.fit(font, node.name(), addX - 2 - nameX), nameX, y + 2,
				SpeakerSprites.TEXT, true);
	}
}
