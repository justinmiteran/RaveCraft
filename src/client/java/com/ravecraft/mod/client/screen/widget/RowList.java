package com.ravecraft.mod.client.screen.widget;

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
 * A recessed panel of fixed-height rows with wheel scrolling and a thin scrollbar, the base of the folder lists.
 * Subclasses draw a row and handle a click on it.
 */
public abstract class RowList extends AbstractWidget {
	protected static final int PAD = 2;
	protected static final int HOVER = 0x30FFFFFF;
	private static final int SCROLLBAR_W = 2;

	private final int rowH;
	private int scroll;

	protected RowList(int x, int y, int width, int height, int rowH, Component label) {
		super(x, y, width, height, label);
		this.rowH = rowH;
	}

	protected abstract int rowCount();

	protected abstract void drawRow(GuiGraphicsExtractor graphics, Font font, int index, int left, int right, int y,
			boolean hovered, int mouseX, int mouseY);

	/** Handles a click on a row; true when it did something. */
	protected abstract boolean clickRow(int index, double mouseX);

	/** Drawn instead of the rows when there are none. */
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
		int index = rowAt(event.x(), event.y());
		if (index >= 0 && clickRow(index, event.x())) {
			playDownSound(Minecraft.getInstance().getSoundManager());
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
			drawRow(graphics, font, i, left, right, rowY, i == hovered, mouseX, mouseY);
		}
		graphics.disableScissor();
		if (max > 0) {
			int trackH = innerHeight();
			int thumbH = Math.max(8, trackH * trackH / (count * rowH));
			int thumbY = innerTop() + (trackH - thumbH) * scroll / max;
			int x = getX() + width - PAD - SCROLLBAR_W;
			graphics.fill(x, innerTop(), x + SCROLLBAR_W, innerTop() + trackH, 0xFF555555);
			graphics.fill(x, thumbY, x + SCROLLBAR_W, thumbY + thumbH, 0xFFE0E0E0);
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
