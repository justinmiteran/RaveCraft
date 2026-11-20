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
	private final Consumer<Path> onAdd;
	private Predicate<Path> covered = path -> false;

	public FolderBrowser(int width, int height, Consumer<Path> onAdd) {
		super(0, 0, width, height, Component.translatable(SpeakerSprites.KEY + "folders.browser"));
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

	@Override
	protected boolean overEnd(Row row, double mouseX) {
		return overRight(mouseX, ADD_W);
	}

	@Override
	protected boolean clickEnd(Row row, double mouseX) {
		if (!addable(row.node())) {
			return false;
		}
		onAdd.accept(path(row.node()).orElseThrow());
		return true;
	}

	@Override
	protected int drawEnd(GuiGraphicsExtractor graphics, Font font, Row row, int right, int y, boolean hovered,
			int mouseX, int mouseY) {
		int addX = right - ADD_W;
		boolean addable = addable(row.node());
		if (hovered && overEnd(row, mouseX)) {
			if (addable) {
				SpeakerSprites.cellHover(graphics, addX - 1, y, right, y + ROW_H);
			}
			Component tip = Component.translatable(SpeakerSprites.KEY + (addable ? "folders.add_this"
					: "folders.inside_existing"));
			graphics.setTooltipForNextFrame(font, tip, mouseX, mouseY);
		} else if (hovered) {
			graphics.setTooltipForNextFrame(font, Component.literal(row.node().id()), mouseX, mouseY);
		}
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SpeakerSprites.PLUS, addX, y + 1, SpeakerSprites.ICON,
				SpeakerSprites.ICON, addable ? 1.0F : 0.3F);
		return addX - 2;
	}
}
