package com.ravecraft.mod.client.screen.widget;

import com.ravecraft.mod.core.library.SourceNode;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

/**
 * The chosen library folders, two lines per folder: the folder icon and its full path, then its track count
 * ({@code …} until counted, {@code Not found} when it is gone), with a ✕ that removes it.
 */
public final class ChosenFoldersList extends RowList {
	public static final int ROW_H = 19;
	private static final int REMOVE_W = SpeakerSprites.ICON + 2;
	private static final int COUNT = 0xFF9A9A9A;

	private final Consumer<Path> onRemove;
	private List<Path> folders = List.of();
	/** What is known of each folder, by path string; a folder missing here is not counted yet. */
	private Map<String, SourceNode> info = Map.of();

	public ChosenFoldersList(int width, int height, Consumer<Path> onRemove) {
		super(0, 0, width, height, ROW_H, Component.translatable(SpeakerSprites.KEY + "folders.title"));
		this.onRemove = onRemove;
	}

	public void setFolders(List<Path> folders) {
		this.folders = List.copyOf(folders);
	}

	public void setInfo(Map<String, SourceNode> info) {
		this.info = Map.copyOf(info);
	}

	@Override
	protected int rowCount() {
		return folders.size();
	}

	private boolean overRemove(double mouseX) {
		return mouseX >= rowRight() - REMOVE_W && mouseX < rowRight();
	}

	@Override
	protected boolean clickRow(int index, double mouseX) {
		if (!overRemove(mouseX)) {
			return false;
		}
		onRemove.accept(folders.get(index));
		return true;
	}

	@Override
	protected void drawEmpty(GuiGraphicsExtractor graphics, Font font) {
		Component empty = Component.translatable(SpeakerSprites.KEY + "no_folders");
		graphics.centeredText(font, SpeakerSprites.fit(font, empty.getString(), width - 2 * PAD - 2), getX() + width / 2,
				innerTop() + 6, SpeakerSprites.MUTED);
	}

	@Override
	protected void drawRow(GuiGraphicsExtractor graphics, Font font, int index, int left, int right, int y,
			boolean hovered, int mouseX, int mouseY) {
		Path folder = folders.get(index);
		SourceNode node = info.get(folder.toString());
		boolean missing = node != null && node.missing();
		int removeX = right - REMOVE_W;
		boolean overRemove = hovered && overRemove(mouseX);
		if (hovered) {
			graphics.fill(left, y, right, y + ROW_H, HOVER);
		}
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SpeakerSprites.FOLDER, left + 2, y + 1, SpeakerSprites.ICON,
				SpeakerSprites.ICON, missing ? 0.4F : 1.0F);
		int textX = left + SpeakerSprites.ICON + 5;
		int textW = removeX - 2 - textX;
		graphics.text(font, SpeakerSprites.fit(font, folder.toString(), textW), textX, y + 2,
				missing ? SpeakerSprites.ABSENT : SpeakerSprites.TEXT, !missing);
		String second;
		if (node == null) {
			second = Component.translatable(SpeakerSprites.KEY + "loading").getString();
		} else if (missing) {
			second = Component.translatable(SpeakerSprites.KEY + "missing").getString();
		} else {
			second = Component.translatable(SpeakerSprites.KEY + "folders.tracks", node.trackCount()).getString();
		}
		graphics.text(font, SpeakerSprites.fit(font, second, textW), textX, y + 10, COUNT, false);
		int iconY = y + (ROW_H - SpeakerSprites.ICON) / 2;
		if (overRemove) {
			graphics.fill(removeX, iconY - 1, removeX + REMOVE_W, iconY + SpeakerSprites.ICON + 1, HOVER);
			graphics.setTooltipForNextFrame(font, Component.translatable(SpeakerSprites.KEY + "remove"), mouseX, mouseY);
		}
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SpeakerSprites.REMOVE, removeX + 1, iconY, SpeakerSprites.ICON,
				SpeakerSprites.ICON);
	}
}
