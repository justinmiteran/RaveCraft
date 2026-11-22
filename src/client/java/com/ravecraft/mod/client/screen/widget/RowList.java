package com.ravecraft.mod.client.screen.widget;

import com.ravecraft.mod.core.ui.RowDrag;
import com.ravecraft.mod.core.ui.Scrollbar;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * A recessed panel of fixed-height rows with wheel scrolling and a thin scrollbar: the base of the queue, the library
 * and the folder lists. Subclasses hand over each row as a {@link ListRow}, built for the frame; this class finds the
 * row under the mouse, lights it up, and hands it the drawing, the click and the tooltip, the row dealing with its own
 * items. A click that did something plays the button sound.
 *
 * <p>A list that reorders gives a {@link #dropRange()}: pressing one of its {@link ListRow#draggable() draggable} rows
 * away from the items and moving past {@link RowDrag#THRESHOLD} drags it, with an insertion line and scrolling near the
 * edges; releasing inside the list calls {@link #onMove}. A press that never moves stays a click on the row.
 */
public abstract class RowList extends AbstractWidget {
	protected static final int PAD = 2;
	protected static final int HOVER = 0x30FFFFFF;
	private static final int SCROLLBAR_W = 2;
	private static final int DIMMED = 0x60000000;
	private static final int DROP_LINE = 0xFF000000 | SpeakerSprites.CURRENT;
	private static final long AUTO_SCROLL_MS = 100;

	private final int rowH;
	private int scroll;
	private final Scrollbar bar = new Scrollbar();
	/** The row pressed for a drag, or -1. */
	private int dragFrom = -1;
	private double pressY;
	private boolean dragging;
	private boolean pressDoubleClick;
	private long lastAutoScroll;

	/** An empty list of rows {@code rowH} high. */
	protected RowList(int x, int y, int width, int height, int rowH, Component label) {
		super(x, y, width, height, label);
		this.rowH = rowH;
	}

	/** How many rows the list holds. */
	protected abstract int rowCount();

	/** Row {@code index}, from 0 to {@link #rowCount()} - 1. */
	protected abstract ListRow row(int index);

	/** Drawn instead of the rows when {@link #rowCount()} is 0. */
	protected void drawEmpty(GuiGraphicsExtractor graphics, Font font) {
	}

	/**
	 * The gaps a dragged row may be dropped at, {@code {min, max}} (gap 0 is above row 0, n below row n−1); null when
	 * the list does not reorder.
	 */
	protected int @Nullable [] dropRange() {
		return null;
	}

	/** Row {@code fromRow} was dropped at {@code gap}, a gap other than the two around it. */
	protected void onMove(int fromRow, int gap) {
	}

	/** Forgets a drag in progress, for a list whose rows changed under it. */
	protected final void cancelDrag() {
		dragFrom = -1;
		dragging = false;
	}

	/** Scrolls back to the top. */
	protected final void resetScroll() {
		scroll = 0;
	}

	/** The top of the first row, scroll aside. */
	protected final int innerTop() {
		return getY() + PAD;
	}

	private int innerHeight() {
		return height - 2 * PAD;
	}

	private int maxScroll() {
		return Math.max(0, rowCount() * rowH - innerHeight());
	}

	/** Right edge of the rows, left of the scrollbar when there is one. */
	protected final int rowRight() {
		return getX() + width - PAD - (maxScroll() > 0 ? SCROLLBAR_W + 1 : 0);
	}

	private boolean inside(double mouseX, double mouseY) {
		return mouseX >= getX() + 1 && mouseX < getX() + width - 1 && mouseY >= innerTop()
				&& mouseY < innerTop() + innerHeight();
	}

	private int rowAt(double mouseX, double mouseY) {
		if (!inside(mouseX, mouseY)) {
			return -1;
		}
		int index = (int) ((mouseY - innerTop() + scroll) / rowH);
		return index >= 0 && index < rowCount() ? index : -1;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (!isActive() || !isValidClickButton(event.buttonInfo())) {
			return false;
		}
		cancelDrag();
		if (onBar(event.x(), event.y())) {
			scroll = bar.press(event.y(), innerTop(), innerHeight(), rowCount() * rowH, scroll);
			return true;
		}
		int index = rowAt(event.x(), event.y());
		return index >= 0 && pressRow(index, event, doubleClick);
	}

	/** A press on row {@code index}: starts a drag on a draggable row away from its items, else clicks the row. */
	private boolean pressRow(int index, MouseButtonEvent event, boolean doubleClick) {
		Font font = Minecraft.getInstance().font;
		ListRow row = row(index);
		if (dropRange() != null && row.draggable() && !row.onItem(font, event.x(), rowRight())) {
			dragFrom = index;
			pressY = event.y();
			dragging = false;
			pressDoubleClick = doubleClick;
			return true;
		}
		if (row.clickAt(font, event.x(), rowRight(), doubleClick)) {
			playDownSound(Minecraft.getInstance().getSoundManager());
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
		if (dragFrom >= 0) {
			dragging |= RowDrag.started(pressY, event.y());
			return true;
		}
		if (bar.dragging()) {
			scroll = bar.drag(event.y(), innerTop(), innerHeight(), rowCount() * rowH);
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		bar.release();
		if (dragFrom < 0) {
			return super.mouseReleased(event);
		}
		int from = dragFrom;
		boolean moved = dragging;
		cancelDrag();
		if (from >= rowCount() || !row(from).draggable()) {
			return true;
		}
		if (moved) {
			drop(from, event);
		} else if (row(from).click(pressDoubleClick)) {
			playDownSound(Minecraft.getInstance().getSoundManager());
		}
		return true;
	}

	/** Row {@code from} released after a drag: moved to the gap under the mouse, unless that leaves it in place. */
	private void drop(int from, MouseButtonEvent event) {
		int[] range = dropRange();
		if (range == null || !inside(event.x(), event.y())) {
			return;
		}
		int gap = gapAt(event.y(), range);
		if (gap != from && gap != from + 1) {
			onMove(from, gap);
		}
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		int max = maxScroll();
		if (max == 0) {
			return false;
		}
		scroll = Mth.clamp(scroll - (int) Math.round(scrollY * rowH), 0, max);
		return true;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Font font = Minecraft.getInstance().font;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SpeakerSprites.INSET, getX(), getY(), width, height);
		int count = rowCount();
		if (count == 0) {
			drawEmpty(graphics, font);
			return;
		}
		int max = maxScroll();
		scroll = Mth.clamp(scroll, 0, max);
		int[] range = dropRange();
		forgetStaleDrag(range, count);
		if (dragging) {
			autoScroll(mouseY, max);
		}
		Frame frame = new Frame(graphics, font, getX() + PAD, rowRight(), mouseX, mouseY);
		graphics.enableScissor(getX() + 1, innerTop(), getX() + width - 1, innerTop() + innerHeight());
		drawRows(frame, count, isHovered() && !dragging ? rowAt(mouseX, mouseY) : -1);
		if (dragging) {
			drawDropLine(frame, range);
		}
		graphics.disableScissor();
		if (max > 0) {
			ScrollbarView.draw(graphics, new ScrollbarView.Track(getX() + width - PAD - SCROLLBAR_W, innerTop(),
					innerHeight()), count * rowH, scroll, bar.dragging() || isHovered() && onBar(mouseX, mouseY));
		}
	}

	/** What drawing the rows of one frame needs: where they run, and where the mouse is. */
	private record Frame(GuiGraphicsExtractor graphics, Font font, int left, int right, int mouseX, int mouseY) {
	}

	/** Drops a drag whose row is gone or no longer draggable, or in a list that stopped reordering. */
	private void forgetStaleDrag(int @Nullable [] range, int count) {
		if (dragFrom >= 0 && (range == null || dragFrom >= count || !row(dragFrom).draggable())) {
			cancelDrag();
		}
	}

	/** Draws the visible rows; {@code hovered} is the row under the mouse, or -1. */
	private void drawRows(Frame frame, int count, int hovered) {
		int bottom = innerTop() + innerHeight();
		for (int i = scroll / rowH; i < count; i++) {
			int rowY = innerTop() + i * rowH - scroll;
			if (rowY >= bottom) {
				break;
			}
			drawRow(frame, i, rowY, i == hovered);
		}
	}

	/** Draws row {@code i} at {@code rowY}: its highlight, the row, the dimming of a dragged row and its tooltip. */
	private void drawRow(Frame frame, int i, int rowY, boolean over) {
		GuiGraphicsExtractor graphics = frame.graphics();
		ListRow row = row(i);
		if (over && row.highlights()) {
			graphics.fill(frame.left(), rowY, frame.right(), rowY + rowH, HOVER);
		}
		row.render(graphics, frame.font(), new ListRow.Box(frame.left(), frame.right(), rowY, rowH), over, frame.mouseX());
		if (dragging && i == dragFrom) {
			graphics.fill(frame.left(), rowY, frame.right(), rowY + rowH, DIMMED);
		}
		if (over) {
			showTooltip(frame, row);
		}
	}

	private void showTooltip(Frame frame, ListRow row) {
		List<Component> tooltip = row.tooltipAt(frame.font(), frame.mouseX(), frame.right());
		if (!tooltip.isEmpty()) {
			frame.graphics().setTooltipForNextFrame(frame.font(), tooltip, Optional.empty(), frame.mouseX(),
					frame.mouseY());
		}
	}

	/** The insertion line at the gap under the mouse. */
	private void drawDropLine(Frame frame, int[] range) {
		int lineY = innerTop() + gapAt(frame.mouseY(), range) * rowH - scroll;
		lineY = Mth.clamp(lineY, innerTop(), innerTop() + innerHeight() - 1);
		frame.graphics().fill(frame.left(), lineY, frame.right(), lineY + 1, DROP_LINE);
	}

	/** The gap nearest to {@code mouseY}, within {@code range}. */
	private int gapAt(double mouseY, int[] range) {
		return RowDrag.gap(mouseY - innerTop() + scroll, rowH, range[0], range[1]);
	}

	/** While dragging near the top or bottom inner edge, scrolls one row at most every {@link #AUTO_SCROLL_MS}. */
	private void autoScroll(int mouseY, int max) {
		long now = Util.getMillis();
		if (now - lastAutoScroll < AUTO_SCROLL_MS) {
			return;
		}
		int step = mouseY < innerTop() + rowH ? -rowH : mouseY >= innerTop() + innerHeight() - rowH ? rowH : 0;
		if (step != 0) {
			scroll = Mth.clamp(scroll + step, 0, max);
			lastAutoScroll = now;
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
