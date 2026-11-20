package com.ravecraft.mod.client.screen.widget;

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

/**
 * A recessed panel of fixed-height rows with wheel scrolling and a thin scrollbar: the base of the queue, the library
 * and the folder lists. Subclasses hand over each row as a {@link ListRow}, built for the frame; this class finds the
 * row under the mouse, lights it up, and hands it the drawing, the click and the tooltip, the row dealing with its own
 * items. A click that did something plays the button sound.
 */
public abstract class RowList extends AbstractWidget {
	protected static final int PAD = 2;
	protected static final int HOVER = 0x30FFFFFF;
	private static final int SCROLLBAR_W = 2;

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
		if (index >= 0 && row(index).clickAt(Minecraft.getInstance().font, event.x(), rowRight(), doubleClick)) {
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
			ListRow row = row(i);
			boolean over = i == hovered;
			if (over && row.highlights()) {
				graphics.fill(left, rowY, right, rowY + rowH, HOVER);
			}
			row.render(graphics, font, left, right, rowY, rowH, over, mouseX);
			if (over) {
				List<Component> tooltip = row.tooltipAt(font, mouseX, right);
				if (!tooltip.isEmpty()) {
					graphics.setTooltipForNextFrame(font, tooltip, Optional.empty(), mouseX, mouseY);
				}
			}
		}
		graphics.disableScissor();
		if (max > 0) {
			ScrollbarView.draw(graphics, getX() + width - PAD - SCROLLBAR_W, innerTop(), innerHeight(), count * rowH,
					scroll, bar.dragging() || isHovered() && onBar(mouseX, mouseY));
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
