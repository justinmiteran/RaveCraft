package com.ravecraft.mod.client.screen.widget;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.client.audio.LocalPlayback;
import com.ravecraft.mod.core.library.LibraryTreeModel;
import com.ravecraft.mod.core.library.LibraryTreeModel.Row;
import com.ravecraft.mod.core.library.MusicSource;
import com.ravecraft.mod.core.library.SourceNode;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * The library as an expandable tree in a recessed panel: the source's roots (the chosen folders), then the
 * children of each expanded folder. Every source call may touch the disk, so it runs on {@link Util#ioPool()} and
 * its result is applied on the client thread, only if the tree has not been reloaded since. Rows show the
 * indentation, an expand arrow, the type icon, the name, the track count of folders and a {@code +} that queues
 * the node's tracks on the speaker. The state and the search filter live in {@link LibraryTreeModel}.
 */
public final class LibraryTree extends AbstractWidget {
	private static final int ROW_H = 11;
	private static final int PAD = 2;
	private static final int INDENT = 8;
	private static final int ARROW_W = 7;
	private static final int SCROLLBAR_W = 2;
	private static final int ADD_W = SpeakerSprites.ICON + 1;
	private static final int HOVER = 0x30FFFFFF;
	private static final int ARROW = 0xFF3A3A3A;

	private final MusicSource source;
	private final BlockPos pos;
	private final LibraryTreeModel model = new LibraryTreeModel();
	private int scroll;

	public LibraryTree(int x, int y, int width, int height, MusicSource source, BlockPos pos) {
		super(x, y, width, height, Component.translatable(SpeakerSprites.KEY + "library"));
		this.source = source;
		this.pos = pos;
		reload();
	}

	/** Forgets the loaded tree and lists the roots again (after the chosen folders changed). */
	public void reload() {
		model.startLoading();
		int generation = model.generation();
		offThread(source::roots, roots -> model.setRoots(generation, roots), () -> model.setRoots(generation, List.of()));
	}

	/** Filters the loaded rows by name; never reads the disk. */
	public void setFilter(String filter) {
		model.setFilter(filter);
		scroll = 0;
	}

	/** True once the roots are known and there are none: the screen then offers to choose a folder. */
	public boolean isEmptyLibrary() {
		return model.isEmpty();
	}

	/** Runs {@code task} on the IO pool, then {@code apply} (or {@code failed}) on the client thread. */
	private static <T> void offThread(Callable<T> task, Consumer<T> apply, Runnable failed) {
		CompletableFuture.supplyAsync(() -> {
			try {
				return task.call();
			} catch (Exception e) {
				throw new CompletionException(e);
			}
		}, Util.ioPool()).whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
			if (error == null) {
				apply.accept(result);
			} else {
				RaveCraft.LOGGER.warn("Could not read the music library", error);
				failed.run();
			}
		}));
	}

	private void toggle(SourceNode folder) {
		if (model.toggle(folder)) {
			int generation = model.generation();
			offThread(() -> source.children(folder), nodes -> model.setChildren(generation, folder.id(), nodes),
					() -> model.setChildren(generation, folder.id(), List.of()));
		}
	}

	private void add(SourceNode node) {
		offThread(() -> source.tracksOf(node), tracks -> {
			if (!tracks.isEmpty()) {
				LocalPlayback.add(pos, tracks);
			}
		}, () -> {
		});
	}

	// --- Layout -----------------------------------------------------------------------------------------------

	private List<Row> rows() {
		return model.rows();
	}

	private int innerTop() {
		return getY() + PAD;
	}

	private int innerHeight() {
		return height - 2 * PAD;
	}

	private int maxScroll() {
		return Math.max(0, rows().size() * ROW_H - innerHeight());
	}

	private int rowLeft() {
		return getX() + PAD;
	}

	private int rowRight() {
		return getX() + width - PAD - (maxScroll() > 0 ? SCROLLBAR_W + 1 : 0);
	}

	/** Index of the visible row under the mouse, or -1 (rows scrolled out of the panel do not count). */
	private int rowAt(double mouseX, double mouseY) {
		if (mouseX < getX() + 1 || mouseX >= getX() + width - 1 || mouseY < innerTop() || mouseY >= innerTop() + innerHeight()) {
			return -1;
		}
		int index = (int) ((mouseY - innerTop() + scroll) / ROW_H);
		return index >= 0 && index < rows().size() ? index : -1;
	}

	private static boolean addable(Row row) {
		return !row.loading() && !row.node().missing();
	}

	private boolean overAdd(double mouseX) {
		return mouseX >= rowRight() - ADD_W && mouseX < rowRight();
	}

	// --- Input ------------------------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (!isActive() || !isValidClickButton(event.buttonInfo())) {
			return false;
		}
		int index = rowAt(event.x(), event.y());
		if (index < 0) {
			return false;
		}
		Row row = rows().get(index);
		if (!addable(row)) {
			return false;
		}
		SourceNode node = row.node();
		if (overAdd(event.x())) {
			playDownSound(Minecraft.getInstance().getSoundManager());
			add(node);
			return true;
		}
		if (node.kind() == SourceNode.Kind.FOLDER) {
			playDownSound(Minecraft.getInstance().getSoundManager());
			toggle(node);
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		int max = maxScroll();
		if (max == 0) {
			return false;
		}
		scroll = Mth.clamp(scroll - (int) Math.round(scrollY * ROW_H), 0, max);
		return true;
	}

	// --- Drawing ----------------------------------------------------------------------------------------------

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Font font = Minecraft.getInstance().font;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SpeakerSprites.INSET, getX(), getY(), width, height);
		if (model.isEmpty()) {
			// The screen puts its "Choose a folder…" button under this message.
			Component empty = Component.translatable(SpeakerSprites.KEY + "no_folders");
			graphics.centeredText(font, SpeakerSprites.fit(font, empty.getString(), width - 2 * PAD - 2),
					getX() + width / 2, innerTop() + 6, SpeakerSprites.MUTED);
			return;
		}
		List<Row> rows = rows();
		scroll = Mth.clamp(scroll, 0, maxScroll());
		int left = rowLeft();
		int right = rowRight();
		int hovered = isHovered() ? rowAt(mouseX, mouseY) : -1;
		graphics.enableScissor(getX() + 1, innerTop(), getX() + width - 1, innerTop() + innerHeight());
		for (int i = scroll / ROW_H; i < rows.size(); i++) {
			int rowY = innerTop() + i * ROW_H - scroll;
			if (rowY >= innerTop() + innerHeight()) {
				break;
			}
			drawRow(graphics, font, rows.get(i), left, right, rowY, i == hovered, mouseX, mouseY);
		}
		graphics.disableScissor();
		drawScrollbar(graphics, rows.size());
	}

	private void drawRow(GuiGraphicsExtractor graphics, Font font, Row row, int left, int right, int rowY,
			boolean hovered, int mouseX, int mouseY) {
		int x = left + 1 + row.depth() * INDENT;
		int textY = rowY + 2;
		if (row.loading()) {
			String loading = Component.translatable(SpeakerSprites.KEY + "loading").getString();
			graphics.text(font, SpeakerSprites.fit(font, loading, right - x - ARROW_W), x + ARROW_W, textY,
					SpeakerSprites.MUTED, false);
			return;
		}
		SourceNode node = row.node();
		boolean missing = node.missing();
		if (hovered && !missing) {
			graphics.fill(left, rowY, right, rowY + ROW_H, HOVER);
		}
		if (node.kind() == SourceNode.Kind.FOLDER && !missing) {
			drawArrow(graphics, x + 1, rowY + 3, row.expanded());
		}
		int iconX = x + ARROW_W;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon(node), iconX, rowY + 1, SpeakerSprites.ICON,
				SpeakerSprites.ICON, missing ? 0.4F : 1.0F);
		int nameX = iconX + SpeakerSprites.ICON + 2;
		int end = right;
		if (missing) {
			if (hovered) {
				graphics.setTooltipForNextFrame(font, Component.translatable(SpeakerSprites.KEY + "missing"), mouseX, mouseY);
			}
		} else {
			int addX = right - ADD_W;
			boolean overAdd = hovered && overAdd(mouseX);
			if (overAdd) {
				graphics.fill(addX, rowY + 1, right - 1, rowY + ROW_H - 1, HOVER);
				graphics.setTooltipForNextFrame(font, Component.translatable(SpeakerSprites.KEY + "add"), mouseX, mouseY);
			}
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SpeakerSprites.PLUS, addX, rowY + 1, SpeakerSprites.ICON,
					SpeakerSprites.ICON);
			end = addX - 2;
			if (node.kind() == SourceNode.Kind.FOLDER && node.trackCount() >= 0) {
				String count = Integer.toString(node.trackCount());
				end -= font.width(count);
				graphics.text(font, count, end, textY, SpeakerSprites.MUTED, false);
				end -= 3;
			}
		}
		String name = SpeakerSprites.fit(font, node.name(), end - nameX);
		graphics.text(font, name, nameX, textY, missing ? SpeakerSprites.ABSENT : SpeakerSprites.TEXT, !missing);
	}

	private static Identifier icon(SourceNode node) {
		return switch (node.kind()) {
			case FOLDER -> SpeakerSprites.FOLDER;
			case PLAYLIST -> SpeakerSprites.PLAYLIST;
			case TRACK -> SpeakerSprites.TRACK;
		};
	}

	/** A 5 px triangle: pointing right when collapsed, down when expanded. */
	private static void drawArrow(GuiGraphicsExtractor graphics, int x, int y, boolean expanded) {
		for (int i = 0; i < 3; i++) {
			if (expanded) {
				graphics.fill(x + i, y + 1 + i, x + 5 - i, y + 2 + i, ARROW);
			} else {
				graphics.fill(x + 1 + i, y + i, x + 2 + i, y + 5 - i, ARROW);
			}
		}
	}

	private void drawScrollbar(GuiGraphicsExtractor graphics, int rowCount) {
		int max = maxScroll();
		if (max == 0) {
			return;
		}
		int trackTop = innerTop();
		int trackH = innerHeight();
		int contentH = rowCount * ROW_H;
		int thumbH = Math.max(8, trackH * trackH / contentH);
		int thumbY = trackTop + (trackH - thumbH) * scroll / max;
		int x = getX() + width - PAD - SCROLLBAR_W;
		graphics.fill(x, trackTop, x + SCROLLBAR_W, trackTop + trackH, 0xFF555555);
		graphics.fill(x, thumbY, x + SCROLLBAR_W, thumbY + thumbH, 0xFFE0E0E0);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
