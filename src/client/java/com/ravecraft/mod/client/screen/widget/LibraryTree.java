package com.ravecraft.mod.client.screen.widget;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.client.audio.LocalPlayback;
import com.ravecraft.mod.client.library.LibraryIndexStore;
import com.ravecraft.mod.client.library.OffThread;
import com.ravecraft.mod.client.screen.SpeakerStates;
import com.ravecraft.mod.core.library.LibraryIndex;
import com.ravecraft.mod.core.library.LibraryTreeModel;
import com.ravecraft.mod.core.library.LibraryTreeModel.Row;
import com.ravecraft.mod.core.library.MusicSource;
import com.ravecraft.mod.core.library.SourceNode;
import com.ravecraft.mod.core.queue.SpeakerQueue;
import java.util.List;
import com.ravecraft.mod.core.ui.Scrollbar;
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

/**
 * The library as an expandable tree in a recessed panel: the source's roots (the chosen folders), then the
 * children of each expanded folder. Every source call may touch the disk, so it runs on the IO pool and
 * its result is applied on the client thread, only if the tree has not been reloaded since. Rows show the
 * indentation, an expand arrow, the type icon, the name, the tracks under each folder (subfolders included) and a
 * {@code +} that queues the node's tracks on the speaker. The flat view lists every track and playlist instead,
 * without folders, each with its folder as a tooltip. Both read the {@link LibraryIndex} kept by
 * {@link LibraryIndexStore}, shown at once and brought up to date in the background; while it is, a last row says
 * so. The state and the search filter live in {@link LibraryTreeModel}.
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
	private static final int SEPARATOR = 0xFF6A6A6A;

	private final MusicSource source;
	private final BlockPos pos;
	private final LibraryTreeModel model = new LibraryTreeModel();
	private int scroll;
	private final Scrollbar bar = new Scrollbar();
	/** The index the rows were last built from, and its version then. */
	private @org.jspecify.annotations.Nullable LibraryIndex index;
	private int indexVersion;

	public LibraryTree(int x, int y, int width, int height, MusicSource source, BlockPos pos) {
		super(x, y, width, height, Component.translatable(SpeakerSprites.KEY + "library"));
		this.source = source;
		this.pos = pos;
		model.startLoading();
		int generation = model.generation();
		OffThread.run(source::roots, roots -> model.setRoots(generation, roots), () -> model.setRoots(generation, List.of()));
		LibraryIndexStore.open(source);
		syncIndex();
	}

	/**
	 * Lists everything again while the current tree and list stay shown (Refresh, or after the chosen folders
	 * changed): the roots and the open folders are read again, the index is walked again in the background.
	 */
	public void reload() {
		int generation = model.generation() + 1;
		List<String> open = model.refresh();
		OffThread.run(source::roots, roots -> model.setRoots(generation, roots), () -> model.setRoots(generation, List.of()));
		for (String id : open) {
			SourceNode folder = new SourceNode(id, SourceNode.Kind.FOLDER, "", -1, false);
			OffThread.run(() -> source.children(folder), nodes -> model.setChildren(generation, id, nodes),
					() -> model.setChildren(generation, id, List.of()));
		}
		LibraryIndexStore.refresh(source);
		syncIndex();
	}

	/** Switches between the folder tree and the flat list. */
	public void setFlat(boolean flat) {
		if (flat != model.flat()) {
			model.setFlat(flat);
			scroll = 0;
		}
	}

	/** Nothing to stop: the index walk goes on after the screen closes and is saved when done. */
	public void close() {
	}

	/** Hands the index's entries to the flat list when the index changed since the last look. */
	private LibraryIndex syncIndex() {
		LibraryIndex current = LibraryIndexStore.get(source);
		if (current != index || current.version() != indexVersion) {
			index = current;
			indexVersion = current.version();
			model.setFlatNodes(current.entries());
		}
		return current;
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

	/** An empty library lets clicks through to the screen's {@code Choose a folder…} button drawn over it. */
	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return !model.isEmpty() && super.isMouseOver(mouseX, mouseY);
	}

	private void toggle(SourceNode folder) {
		if (model.toggle(folder)) {
			int generation = model.generation();
			OffThread.run(() -> source.children(folder), nodes -> model.setChildren(generation, folder.id(), nodes),
					() -> model.setChildren(generation, folder.id(), List.of()));
		}
	}

	private void add(SourceNode node) {
		OffThread.run(() -> source.tracksOf(node), tracks -> {
			// Cap to the room left now, so no entry is registered for an id the server would drop.
			int room = Math.min(tracks.size(), SpeakerQueue.roomLeft(queued()));
			if (room > 0) {
				LocalPlayback.add(pos, tracks.subList(0, room));
			}
		}, () -> {
		});
	}

	/** Entries in the queue as last reported by the server. */
	private int queued() {
		return SpeakerStates.get(pos).map(state -> state.entries().size()).orElse(0);
	}

	private boolean full() {
		return SpeakerQueue.roomLeft(queued()) == 0;
	}

	// --- Layout -----------------------------------------------------------------------------------------------

	private List<Row> rows() {
		syncIndex();
		return model.rows();
	}

	/** The last row: the index being brought up to date, or, in the flat view, that it stopped at its limit. */
	private boolean footerShown() {
		return LibraryIndexStore.refreshing(source) || model.flat() && syncIndex().truncated();
	}

	private int rowCount() {
		return rows().size() + (footerShown() ? 1 : 0);
	}

	private int innerTop() {
		return getY() + PAD;
	}

	private int innerHeight() {
		return height - 2 * PAD;
	}

	private int maxScroll() {
		return Math.max(0, rowCount() * ROW_H - innerHeight());
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
		return index >= 0 && index < rowCount() ? index : -1;
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
		if (onBar(event.x(), event.y())) {
			scroll = bar.press(event.y(), innerTop(), innerHeight(), rowCount() * ROW_H, scroll);
			return true;
		}
		int index = rowAt(event.x(), event.y());
		if (index < 0) {
			return false;
		}
		if (index == rows().size()) {
			return false;
		}
		Row row = rows().get(index);
		if (!addable(row)) {
			return false;
		}
		SourceNode node = row.node();
		if (overAdd(event.x())) {
			if (full()) {
				return true;
			}
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

	/** True when a press at {@code (x, y)} lands on the scrollbar while there is something to scroll. */
	private boolean onBar(double x, double y) {
		return maxScroll() > 0 && Scrollbar.hits(x, getX() + width - PAD - SCROLLBAR_W, getX() + width) && y >= innerTop()
				&& y < innerTop() + innerHeight();
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (bar.dragging()) {
			scroll = bar.drag(event.y(), innerTop(), innerHeight(), rowCount() * ROW_H);
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		bar.release();
		return super.mouseReleased(event);
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
		int count = rowCount();
		for (int i = scroll / ROW_H; i < count; i++) {
			int rowY = innerTop() + i * ROW_H - scroll;
			if (rowY >= innerTop() + innerHeight()) {
				break;
			}
			if (i == rows.size()) {
				drawFooter(graphics, font, left, right, rowY, i == hovered, mouseX, mouseY);
			} else {
				drawRow(graphics, font, rows.get(i), left, right, rowY, i == hovered, mouseX, mouseY);
			}
		}
		graphics.disableScissor();
		drawScrollbar(graphics, count, mouseX, mouseY);
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
		// The flat list has no folders, so no arrow column either.
		int iconX = model.flat() ? x + 1 : x + ARROW_W;
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
				Component tip = full()
						? Component.translatable(SpeakerSprites.KEY + "queue_full", SpeakerQueue.MAX_ENTRIES)
						: Component.translatable(SpeakerSprites.KEY + "add");
				graphics.setTooltipForNextFrame(font, tip, mouseX, mouseY);
			} else if (hovered && model.flat()) {
				String folder = source.parentName(node);
				if (!folder.isEmpty()) {
					graphics.setTooltipForNextFrame(font,
							Component.translatable(SpeakerSprites.KEY + "in_folder", folder), mouseX, mouseY);
				}
			}
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SpeakerSprites.PLUS, addX, rowY + 1, SpeakerSprites.ICON,
					SpeakerSprites.ICON, full() ? 0.4F : 1.0F);
			end = addX - 2;
			if (node.kind() == SourceNode.Kind.FOLDER) {
				String count = countLabel(node);
				end -= font.width(count);
				graphics.text(font, count, end, textY, SpeakerSprites.MUTED, false);
				end -= 3;
			}
		}
		String name = SpeakerSprites.fit(font, node.name(), end - nameX);
		graphics.text(font, name, nameX, textY, missing ? SpeakerSprites.ABSENT : SpeakerSprites.TEXT, !missing);
	}

	/** The tracks under a folder; {@code …} while the index has not seen it yet. */
	private String countLabel(SourceNode folder) {
		return LibraryIndexStore.countLabel(source.id(), folder.id()).orElse("…");
	}

	private void drawFooter(GuiGraphicsExtractor graphics, Font font, int left, int right, int rowY, boolean hovered,
			int mouseX, int mouseY) {
		LibraryIndex current = syncIndex();
		boolean refreshing = LibraryIndexStore.refreshing(source);
		if (hovered && current.truncated()) {
			graphics.setTooltipForNextFrame(font,
					Component.translatable(SpeakerSprites.KEY + "flat.limit", LibraryIndex.LIMIT), mouseX, mouseY);
		}
		graphics.fill(left + 1, rowY, right - 1, rowY + 1, SEPARATOR);
		int textY = rowY + 2;
		String found = current.size() + (current.truncated() ? "+" : "");
		int countX = right - 2 - font.width(found);
		if (refreshing) {
			String key = current.complete() ? "flat.updating" : "flat.searching";
			String label = Component.translatable(SpeakerSprites.KEY + key).getString();
			graphics.text(font, SpeakerSprites.fit(font, label, countX - left - 6), left + 2, textY,
					SpeakerSprites.MUTED, true);
		}
		graphics.text(font, found, countX, textY, SpeakerSprites.MUTED, true);
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

	private void drawScrollbar(GuiGraphicsExtractor graphics, int rowCount, int mouseX, int mouseY) {
		if (maxScroll() > 0) {
			ScrollbarView.draw(graphics, getX() + width - PAD - SCROLLBAR_W, innerTop(), innerHeight(),
					rowCount * ROW_H, scroll, bar.dragging() || isHovered() && onBar(mouseX, mouseY));
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
