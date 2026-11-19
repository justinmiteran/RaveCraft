package com.ravecraft.mod.client.screen.widget;

import com.ravecraft.mod.client.screen.SpeakerStates;
import com.ravecraft.mod.network.payload.SpeakerCommandPayload.Command;
import com.ravecraft.mod.network.payload.SpeakerStatePayload;
import com.ravecraft.mod.network.payload.SpeakerStatePayload.StateEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * The speaker's queue in the order the server sends it, in a recessed panel: the current row in yellow, the owner
 * on the right (greyed when away), a ✕ per row sending {@code REMOVE}, and a {@code Next round} separator at
 * {@code nextRoundIndex}. Scrolls with the mouse wheel when the rows do not fit.
 */
public final class QueuePanel extends AbstractWidget {
	private static final int ROW_H = 12;
	private static final int PAD = 2;
	private static final int SCROLLBAR_W = 2;
	private static final int REMOVE_W = SpeakerSprites.ICON + 1;
	private static final int SEPARATOR = 0xFF5A5A5A;
	private static final int HOVER = 0x30FFFFFF;

	/** One line of the panel: an entry, or the separator when {@code entry} is null. */
	private record Row(@Nullable StateEntry entry, boolean current) {
	}

	private final BlockPos pos;
	private List<Row> rows = List.of();
	private int scroll;
	private final Scrollbar bar = new Scrollbar();

	public QueuePanel(int x, int y, int width, int height, BlockPos pos) {
		super(x, y, width, height, Component.translatable(SpeakerSprites.KEY + "queue"));
		this.pos = pos;
	}

	/** Rebuilds the rows from a new state, keeping the scroll position when it still fits. */
	public void show(@Nullable SpeakerStatePayload state) {
		List<Row> built = new ArrayList<>();
		if (state != null) {
			List<StateEntry> entries = state.entries();
			for (int i = 0; i < entries.size(); i++) {
				if (i == state.nextRoundIndex()) {
					built.add(new Row(null, false));
				}
				StateEntry entry = entries.get(i);
				built.add(new Row(entry, state.current().filter(entry.entryId()::equals).isPresent()));
			}
		}
		rows = List.copyOf(built);
		scroll = Mth.clamp(scroll, 0, maxScroll());
	}

	// --- Layout -----------------------------------------------------------------------------------------------

	private int innerTop() {
		return getY() + PAD;
	}

	private int innerHeight() {
		return height - 2 * PAD;
	}

	private int maxScroll() {
		return Math.max(0, rows.size() * ROW_H - innerHeight());
	}

	private int rowRight() {
		return getX() + width - PAD - (maxScroll() > 0 ? SCROLLBAR_W + 1 : 0);
	}

	/** Index of the row under {@code mouseY}, or -1. */
	private int rowAt(double mouseX, double mouseY) {
		if (mouseX < getX() + 1 || mouseX >= getX() + width - 1 || mouseY < innerTop() || mouseY >= innerTop() + innerHeight()) {
			return -1;
		}
		int index = (int) ((mouseY - innerTop() + scroll) / ROW_H);
		return index >= 0 && index < rows.size() ? index : -1;
	}

	private boolean overRemove(double mouseX) {
		return mouseX >= rowRight() - REMOVE_W && mouseX < rowRight();
	}

	// --- Input ------------------------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (!isActive() || !isValidClickButton(event.buttonInfo())) {
			return false;
		}
		if (onBar(event.x(), event.y())) {
			scroll = bar.press(event.y(), innerTop(), innerHeight(), rows.size() * ROW_H, scroll);
			return true;
		}
		int index = rowAt(event.x(), event.y());
		if (index < 0 || rows.get(index).entry() == null || !overRemove(event.x())) {
			return false;
		}
		playDownSound(Minecraft.getInstance().getSoundManager());
		SpeakerStates.send(pos, Command.REMOVE, Optional.of(rows.get(index).entry().entryId()));
		return true;
	}

	/** True when a press at {@code (x, y)} lands on the scrollbar while there is something to scroll. */
	private boolean onBar(double x, double y) {
		return maxScroll() > 0 && Scrollbar.hits(x, getX() + width - PAD - SCROLLBAR_W, getX() + width) && y >= innerTop()
				&& y < innerTop() + innerHeight();
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (bar.dragging()) {
			scroll = bar.drag(event.y(), innerTop(), innerHeight(), rows.size() * ROW_H);
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
		int left = getX() + PAD;
		int right = rowRight();
		if (rows.isEmpty()) {
			Component empty = Component.translatable(SpeakerSprites.KEY + "queue_empty");
			graphics.centeredText(font, SpeakerSprites.fit(font, empty.getString(), width - 2 * PAD - 2),
					getX() + width / 2, innerTop() + 4, SpeakerSprites.MUTED);
			return;
		}
		int hovered = isHovered() ? rowAt(mouseX, mouseY) : -1;
		graphics.enableScissor(getX() + 1, innerTop(), getX() + width - 1, innerTop() + innerHeight());
		int first = scroll / ROW_H;
		for (int i = first; i < rows.size(); i++) {
			int rowY = innerTop() + i * ROW_H - scroll;
			if (rowY >= innerTop() + innerHeight()) {
				break;
			}
			Row row = rows.get(i);
			if (row.entry() == null) {
				drawSeparator(graphics, font, left, right, rowY);
			} else {
				drawEntry(graphics, font, row, left, right, rowY, i == hovered, mouseX);
			}
		}
		graphics.disableScissor();
		drawScrollbar(graphics, mouseX, mouseY);
	}

	private void drawSeparator(GuiGraphicsExtractor graphics, Font font, int left, int right, int rowY) {
		String label = SpeakerSprites.fit(font, Component.translatable(SpeakerSprites.KEY + "next_round").getString(),
				right - left - 12);
		int labelW = font.width(label);
		int centre = (left + right) / 2;
		int lineY = rowY + ROW_H / 2;
		graphics.fill(left + 2, lineY, centre - labelW / 2 - 3, lineY + 1, SEPARATOR);
		graphics.fill(centre + labelW / 2 + 3, lineY, right - 2, lineY + 1, SEPARATOR);
		graphics.text(font, label, centre - labelW / 2, rowY + 2, SEPARATOR, false);
	}

	private void drawEntry(GuiGraphicsExtractor graphics, Font font, Row row, int left, int right, int rowY,
			boolean hovered, int mouseX) {
		StateEntry entry = row.entry();
		int textY = rowY + 2;
		boolean overRemove = hovered && overRemove(mouseX);
		if (hovered) {
			graphics.fill(left, rowY, right, rowY + ROW_H, HOVER);
		}
		int titleX = left + 8;
		if (row.current()) {
			graphics.text(font, ">", left + 1, textY, SpeakerSprites.CURRENT);
		}
		int removeX = right - REMOVE_W;
		if (overRemove) {
			graphics.fill(removeX, rowY + 1, right - 1, rowY + ROW_H - 1, HOVER);
			graphics.setTooltipForNextFrame(font, Component.translatable(SpeakerSprites.KEY + "remove"), mouseX, rowY);
		}
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SpeakerSprites.REMOVE, removeX, rowY + 1, SpeakerSprites.ICON,
				SpeakerSprites.ICON);

		int space = removeX - 3 - titleX;
		String owner = SpeakerSprites.fit(font, entry.ownerName(), space / 3);
		int ownerX = removeX - 3 - font.width(owner);
		String title = SpeakerSprites.fit(font, entry.title(), ownerX - 4 - titleX);
		if (entry.ownerOnline()) {
			graphics.text(font, title, titleX, textY, row.current() ? SpeakerSprites.CURRENT : SpeakerSprites.TEXT);
			graphics.text(font, owner, ownerX, textY, SpeakerSprites.MUTED);
		} else {
			graphics.text(font, title, titleX, textY, SpeakerSprites.ABSENT, false);
			graphics.text(font, owner, ownerX, textY, SpeakerSprites.ABSENT, false);
		}
	}

	private void drawScrollbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (maxScroll() > 0) {
			ScrollbarView.draw(graphics, getX() + width - PAD - SCROLLBAR_W, innerTop(), innerHeight(),
					rows.size() * ROW_H, scroll, bar.dragging() || isHovered() && onBar(mouseX, mouseY));
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
