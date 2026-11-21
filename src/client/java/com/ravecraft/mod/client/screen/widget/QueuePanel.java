package com.ravecraft.mod.client.screen.widget;

import com.ravecraft.mod.client.screen.SpeakerStates;
import com.ravecraft.mod.core.ui.RowDrag;
import com.ravecraft.mod.network.payload.SpeakerCommandPayload.Command;
import com.ravecraft.mod.network.payload.SpeakerStatePayload;
import com.ravecraft.mod.network.payload.SpeakerStatePayload.StateEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The speaker's queue in the order the server sends it, in a recessed panel: the current row in yellow, the owner
 * on the right (greyed when away), a ✕ per row sending {@code REMOVE}, and a {@code Next round} separator at
 * {@code nextRoundIndex}. Scrolls with the mouse wheel when the rows do not fit. Entries other than the current one
 * are dragged into what is still to play, between the current row and the separator, which sends {@code MOVE}.
 */
public final class QueuePanel extends RowList {
	private static final int ROW_H = 12;
	private static final int SEPARATOR = 0xFF5A5A5A;

	/** One line of the panel: an entry, or the separator when {@code entry} is null. */
	private record Row(@Nullable StateEntry entry, boolean current) {
	}

	private final UUID speakerId;
	private List<Row> rows = List.of();

	/** The queue of speaker {@code speakerId}. */
	public QueuePanel(int x, int y, int width, int height, UUID speakerId) {
		super(x, y, width, height, ROW_H, Component.translatable(SpeakerSprites.KEY + "queue"));
		this.speakerId = speakerId;
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
		List<Row> shown = List.copyOf(built);
		if (!ids(shown).equals(ids(rows))) {
			cancelDrag();
		}
		rows = shown;
	}

	private static List<@Nullable UUID> ids(List<Row> rows) {
		return rows.stream().map(row -> row.entry() == null ? null : row.entry().entryId()).toList();
	}

	/** The gap right after the current row: 1 when the first row is the current entry, else 0. */
	private int firstGap() {
		return !rows.isEmpty() && rows.get(0).current() ? 1 : 0;
	}

	@Override
	protected int @Nullable [] dropRange() {
		if (rows.stream().filter(row -> row.entry() != null).count() < 2) {
			return null;
		}
		int end = rows.size();
		for (int i = 0; i < rows.size(); i++) {
			if (rows.get(i).entry() == null) {
				end = i;
				break;
			}
		}
		return new int[] {firstGap(), end};
	}

	@Override
	protected void onMove(int fromRow, int gap) {
		StateEntry entry = rows.get(fromRow).entry();
		int index = RowDrag.moveIndex(fromRow, gap, firstGap());
		if (entry != null && index >= 0) {
			SpeakerStates.send(speakerId, Command.MOVE, Optional.of(entry.entryId()), index);
		}
	}

	@Override
	protected int rowCount() {
		return rows.size();
	}

	@Override
	protected void drawEmpty(GuiGraphicsExtractor graphics, Font font) {
		Component empty = Component.translatable(SpeakerSprites.KEY + "queue_empty");
		graphics.centeredText(font, SpeakerSprites.fit(font, empty.getString(), width - 2 * PAD - 2), getX() + width / 2,
				innerTop() + 4, SpeakerSprites.MUTED);
	}

	@Override
	protected ListRow row(int index) {
		Row row = rows.get(index);
		StateEntry entry = row.entry();
		if (entry == null) {
			return new ListRow() {
				@Override
				public boolean highlights() {
					return false;
				}

				@Override
				public void draw(GuiGraphicsExtractor graphics, Font font, int left, int end, int y) {
					drawSeparator(graphics, font, left, end, y);
				}
			};
		}
		RowItem remove = RowItem.button(SpeakerSprites.REMOVE, Component.translatable(SpeakerSprites.KEY + "remove"),
				() -> SpeakerStates.send(speakerId, Command.REMOVE, Optional.of(entry.entryId())));
		return new ListRow() {
			@Override
			public List<RowItem> items() {
				return List.of(remove);
			}

			@Override
			public boolean draggable() {
				return !row.current();
			}

			@Override
			public void draw(GuiGraphicsExtractor graphics, Font font, int left, int end, int y) {
				drawEntry(graphics, font, row, left, end, y);
			}
		};
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

	/** Draws the entry of {@code row}: its title and its owner, dimmed when the owner is offline. */
	private void drawEntry(GuiGraphicsExtractor graphics, Font font, Row row, int left, int end, int rowY) {
		StateEntry entry = row.entry();
		int textY = rowY + 2;
		int titleX = left + 8;
		if (row.current()) {
			graphics.text(font, ">", left + 1, textY, SpeakerSprites.CURRENT);
		}
		int space = end - titleX;
		String owner = SpeakerSprites.fit(font, entry.ownerName(), space / 3);
		int ownerX = end - font.width(owner);
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
