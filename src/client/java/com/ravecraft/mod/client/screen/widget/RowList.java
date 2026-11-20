package com.ravecraft.mod.client.screen.widget;

import com.ravecraft.mod.core.ui.Scrollbar;
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
import org.jspecify.annotations.Nullable;

/**
 * A recessed panel of fixed-height rows with wheel scrolling and a thin scrollbar: the base of the queue, the library
 * and the folder lists. Subclasses hand over each row as a {@link ListRow}, built for the frame; this class lights up
 * the hovered row, lays out and draws its {@link RowItem}s from the right edge, shows the tooltips, and passes a click
 * to the item under the mouse, or else to the row. A click that did something plays the button sound.
 */
public abstract class RowList extends AbstractWidget {
	protected static final int PAD = 2;
	protected static final int HOVER = 0x30FFFFFF;
	private static final int SCROLLBAR_W = 2;
	/** Space between two items, and between the items and the rest of the row. */
	private static final int GAP = 2;

	private final int rowH;
	private int scroll;
	private final Scrollbar bar = new Scrollbar();

	protected RowList(int x, int y, int width, int height, int rowH, Component label) {
		super(x, y, width, height, label);
		this.rowH = rowH;
	}

	protected abstract int rowCount();

	/** Row {@code index}, from 0 to {@link #rowCount()} - 1. */
	protected abstract ListRow row(int index);

	/** Drawn instead of the rows when {@link #rowCount()} is 0. */
	protected void drawEmpty(GuiGraphicsExtractor graphics, Font font) {
	}

	protected final void resetScroll() {
		scroll = 0;
	}

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

	private int rowAt(double mouseX, double mouseY) {
		if (mouseX < getX() + 1 || mouseX >= getX() + width - 1 || mouseY < innerTop() || mouseY >= innerTop() + innerHeight()) {
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
		if (onBar(event.x(), event.y())) {
			scroll = bar.press(event.y(), innerTop(), innerHeight(), rowCount() * rowH, scroll);
			return true;
		}
		int index = rowAt(event.x(), event.y());
		if (index >= 0 && click(row(index), event.x(), doubleClick)) {
			playDownSound(Minecraft.getInstance().getSoundManager());
			return true;
		}
		return false;
	}

	/** The item of {@code row} under {@code mouseX}, laid out from the right edge as {@link #drawRow} does. */
	private @Nullable RowItem itemAt(ListRow row, double mouseX) {
		Font font = Minecraft.getInstance().font;
		int x = rowRight();
		for (RowItem item : row.items()) {
			int w = item.width(font);
			x -= w;
			if (mouseX >= x && mouseX < x + w) {
				return item;
			}
			x -= GAP;
		}
		return null;
	}

	private boolean click(ListRow row, double mouseX, boolean doubleClick) {
		RowItem item = itemAt(row, mouseX);
		return item != null ? item.click() : row.click(doubleClick);
	}

	/** True when a press at {@code (x, y)} lands on the scrollbar while there is something to scroll. */
	private boolean onBar(double x, double y) {
		return maxScroll() > 0 && Scrollbar.hits(x, getX() + width - PAD - SCROLLBAR_W, getX() + width) && y >= innerTop()
				&& y < innerTop() + innerHeight();
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (bar.dragging()) {
			scroll = bar.drag(event.y(), innerTop(), innerHeight(), rowCount() * rowH);
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
		int left = getX() + PAD;
		int right = rowRight();
		int hovered = isHovered() ? rowAt(mouseX, mouseY) : -1;
		graphics.enableScissor(getX() + 1, innerTop(), getX() + width - 1, innerTop() + innerHeight());
		for (int i = scroll / rowH; i < count; i++) {
			int rowY = innerTop() + i * rowH - scroll;
			if (rowY >= innerTop() + innerHeight()) {
				break;
			}
			drawRow(graphics, font, row(i), left, right, rowY, i == hovered, mouseX, mouseY);
		}
		graphics.disableScissor();
		if (max > 0) {
			ScrollbarView.draw(graphics, getX() + width - PAD - SCROLLBAR_W, innerTop(), innerHeight(), count * rowH,
					scroll, bar.dragging() || isHovered() && onBar(mouseX, mouseY));
		}
	}

	private void drawRow(GuiGraphicsExtractor graphics, Font font, ListRow row, int left, int right, int y,
			boolean hovered, int mouseX, int mouseY) {
		if (hovered && row.highlights()) {
			graphics.fill(left, y, right, y + rowH, HOVER);
		}
		int x = right;
		Component itemTip = null;
		boolean overItem = false;
		for (RowItem item : row.items()) {
			int w = item.width(font);
			x -= w;
			boolean over = hovered && mouseX >= x && mouseX < x + w;
			if (hovered || !item.hoverOnly()) {
				item.draw(graphics, font, x, y, rowH, over);
			}
			if (over) {
				overItem = true;
				itemTip = item.tooltip();
			}
			x -= GAP;
		}
		row.draw(graphics, font, left, row.items().isEmpty() ? right : x, y);
		if (itemTip != null) {
			graphics.setTooltipForNextFrame(font, itemTip, mouseX, mouseY);
		} else if (hovered && !overItem && !row.tooltip().isEmpty()) {
			graphics.setTooltipForNextFrame(font, row.tooltip(), Optional.empty(), mouseX, mouseY);
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
