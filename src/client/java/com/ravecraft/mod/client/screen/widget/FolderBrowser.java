package com.ravecraft.mod.client.screen.widget;

import com.ravecraft.mod.client.library.OffThread;
import com.ravecraft.mod.core.library.LibraryEntry;
import com.ravecraft.mod.core.library.MusicLibrary;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The in-game folder browser, used when the system dialog is unavailable or chosen on purpose. It shows folders
 * only: first the drives and the home folders, then, inside a folder, {@code ..} and its sub-folders sorted by
 * name. Listing runs off the render thread; a result is applied only if the player has not moved on since.
 */
public final class FolderBrowser extends RowList {
	private static final int ROW_H = 11;

	private @Nullable Path current;
	private List<LibraryEntry> entries = List.of();
	private boolean loading;
	private int generation;

	public FolderBrowser(int width, int height) {
		super(0, 0, width, height, ROW_H, Component.translatable(SpeakerSprites.KEY + "folders.browser"));
	}

	/** The folder shown, or null on the list of drives. */
	public @Nullable Path current() {
		return current;
	}

	/** Shows {@code folder}'s sub-folders, or the drives when it is null. */
	public void open(@Nullable Path folder) {
		current = folder;
		entries = List.of();
		loading = true;
		resetScroll();
		int requested = ++generation;
		OffThread.run(() -> folder == null ? MusicLibrary.roots() : subFolders(folder),
				list -> show(requested, list), () -> show(requested, List.of()));
	}

	private static List<LibraryEntry> subFolders(Path folder) throws Exception {
		List<LibraryEntry> folders = new ArrayList<>();
		for (LibraryEntry entry : MusicLibrary.list(folder)) {
			if (entry.isBrowsable()) {
				folders.add(entry);
			}
		}
		return folders;
	}

	private void show(int requested, List<LibraryEntry> list) {
		if (requested == generation) {
			entries = List.copyOf(list);
			loading = false;
		}
	}

	private boolean hasParentRow() {
		return current != null;
	}

	@Override
	protected int rowCount() {
		return (hasParentRow() ? 1 : 0) + (loading ? 1 : entries.size());
	}

	@Override
	protected boolean clickRow(int index, double mouseX) {
		if (hasParentRow() && index == 0) {
			open(current.getParent());
			return true;
		}
		if (loading) {
			return false;
		}
		open(entries.get(index - (hasParentRow() ? 1 : 0)).path());
		return true;
	}

	@Override
	protected void drawEmpty(GuiGraphicsExtractor graphics, Font font) {
		// Only the drive list can be empty (a folder always has its ".." row).
	}

	@Override
	protected void drawRow(GuiGraphicsExtractor graphics, Font font, int index, int left, int right, int y,
			boolean hovered, int mouseX, int mouseY) {
		int iconX = left + 2;
		int textX = iconX + SpeakerSprites.ICON + 3;
		boolean parent = hasParentRow() && index == 0;
		if (!parent && loading) {
			String text = Component.translatable(SpeakerSprites.KEY + "loading").getString();
			graphics.text(font, SpeakerSprites.fit(font, text, right - textX), textX, y + 2, SpeakerSprites.MUTED, false);
			return;
		}
		if (hovered) {
			graphics.fill(left, y, right, y + ROW_H, HOVER);
		}
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SpeakerSprites.FOLDER, iconX, y + 1, SpeakerSprites.ICON,
				SpeakerSprites.ICON, parent ? 0.5F : 1.0F);
		String name = parent ? ".." : entries.get(index - (hasParentRow() ? 1 : 0)).name();
		graphics.text(font, SpeakerSprites.fit(font, name, right - 2 - textX), textX, y + 2, SpeakerSprites.TEXT, true);
	}
}
