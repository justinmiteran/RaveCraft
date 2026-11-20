package com.ravecraft.mod.client.screen.widget;

import com.ravecraft.mod.client.screen.SpeakerStates;
import com.ravecraft.mod.network.payload.SpeakerCommandPayload.Command;
import com.ravecraft.mod.network.payload.SpeakerStatePayload;
import com.ravecraft.mod.network.payload.SpeakerStatePayload.StateEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The speaker's queue in the order the server sends it, in a recessed panel: the current row in yellow, the owner
 * on the right (greyed when away), a ✕ per row sending {@code REMOVE}, and a {@code Next round} separator at
 * {@code nextRoundIndex}. Scrolls with the mouse wheel when the rows do not fit.
 */
public final class QueuePanel extends RowList {
	private static final int ROW_H = 12;
	private static final int REMOVE_W = SpeakerSprites.ICON + 1;
	private static final int SEPARATOR = 0xFF5A5A5A;

	/** One line of the panel: an entry, or the separator when {@code entry} is null. */
	private record Row(@Nullable StateEntry entry, boolean current) {
	}

	private final BlockPos pos;
	private List<Row> rows = List.of();

	public QueuePanel(int x, int y, int width, int height, BlockPos pos) {
		super(x, y, width, height, ROW_H, Component.translatable(SpeakerSprites.KEY + "queue"));
		this.pos = pos;
	}

	/** Rebuilds the rows from a new state; the scroll position is kept while it still fits. */
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
	}

	@Override
	protected int rowCount() {
		return rows.size();
	}

	private boolean overRemove(double mouseX) {
		return mouseX >= rowRight() - REMOVE_W && mouseX < rowRight();
	}

	@Override
	protected boolean clickRow(int index, double mouseX, boolean doubleClick) {
		StateEntry entry = rows.get(index).entry();
		if (entry == null || !overRemove(mouseX)) {
			return false;
		}
		SpeakerStates.send(pos, Command.REMOVE, Optional.of(entry.entryId()));
		return true;
	}

	@Override
	protected void drawEmpty(GuiGraphicsExtractor graphics, Font font) {
		Component empty = Component.translatable(SpeakerSprites.KEY + "queue_empty");
		graphics.centeredText(font, SpeakerSprites.fit(font, empty.getString(), width - 2 * PAD - 2), getX() + width / 2,
				innerTop() + 4, SpeakerSprites.MUTED);
	}

	@Override
	protected void drawRow(GuiGraphicsExtractor graphics, Font font, int index, int left, int right, int rowY,
			boolean hovered, int mouseX, int mouseY) {
		Row row = rows.get(index);
		if (row.entry() == null) {
			drawSeparator(graphics, font, left, right, rowY);
		} else {
			drawEntry(graphics, font, row, left, right, rowY, hovered, mouseX);
		}
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
			SpeakerSprites.cellHover(graphics, removeX - 1, rowY, right, rowY + ROW_H);
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
}
