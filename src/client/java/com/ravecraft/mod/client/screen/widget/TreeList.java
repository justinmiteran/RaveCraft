package com.ravecraft.mod.client.screen.widget;

import com.ravecraft.mod.client.library.OffThread;
import com.ravecraft.mod.core.library.LibraryTreeModel;
import com.ravecraft.mod.core.library.LibraryTreeModel.Row;
import com.ravecraft.mod.core.library.SourceNode;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * A {@link RowList} showing a {@link LibraryTreeModel}: the base of the library, the chosen folders and the folder
 * browser. Subclasses say how to list the roots and a folder's children; this class runs those listings on the IO
 * pool and applies them on the client thread, only if the tree was not reloaded meanwhile and is not closed.
 *
 * <p>Every row is drawn the same way, from the left: indentation, expand arrow, icon, name; subclasses draw what
 * goes on the right (buttons, counts) with {@link #drawEnd} and handle a click there with {@link #clickEnd}. A click
 * anywhere else opens or folds a folder.
 */
public abstract class TreeList extends RowList {
	public static final int ROW_H = 11;
	protected static final int INDENT = 8;
	protected static final int ARROW_W = 7;
	/** The {@code +} button at the right of a row. */
	protected static final int ADD_W = SpeakerSprites.ICON + 1;

	protected final LibraryTreeModel model = new LibraryTreeModel();
	private boolean closed;

	protected TreeList(int x, int y, int width, int height, Component label) {
		super(x, y, width, height, ROW_H, label);
	}

	/** The top-level folders; runs on the IO pool. */
	protected abstract List<SourceNode> listRoots() throws Exception;

	/** What an expanded folder shows; runs on the IO pool. */
	protected abstract List<SourceNode> listChildren(String folderId) throws Exception;

	/** Lists the roots again, everything folded. */
	protected final void loadRoots() {
		model.startLoading();
		resetScroll();
		applyRoots(model.generation());
	}

	/**
	 * Lists everything again while the current rows stay shown: the roots, and the children of every open folder.
	 */
	protected final void reloadAll() {
		List<String> open = model.refresh();
		int generation = model.generation();
		applyRoots(generation);
		for (String id : open) {
			applyChildren(generation, id);
		}
	}

	/** Opens or folds {@code folder}, listing its children when it opens for the first time. */
	protected final void toggle(SourceNode folder) {
		if (model.toggle(folder)) {
			loadChildren(folder.id());
		}
	}

	/** Lists the children of a folder the model just opened. */
	protected final void loadChildren(String id) {
		applyChildren(model.generation(), id);
	}

	/** Stops applying late listings once the tree is gone. */
	public void close() {
		closed = true;
	}

	private void applyRoots(int generation) {
		OffThread.run(this::listRoots, roots -> {
			if (!closed) {
				model.setRoots(generation, roots);
			}
		}, () -> model.setRoots(generation, List.of()));
	}

	private void applyChildren(int generation, String id) {
		OffThread.run(() -> listChildren(id), nodes -> {
			if (!closed) {
				model.setChildren(generation, id, nodes);
			}
		}, () -> model.setChildren(generation, id, List.of()));
	}

	// --- Rows -----------------------------------------------------------------------------------------------

	/** The rows on show; the library brings its index up to date first. */
	protected List<Row> rows() {
		return model.rows();
	}

	@Override
	protected int rowCount() {
		return model.isEmpty() ? 0 : rows().size();
	}

	/** False for a row that reacts to nothing (a missing folder in the library): no hover, no click. */
	protected boolean clickable(Row row) {
		return true;
	}

	/** Greyed rows: name, icon and arrow dimmed, and the folder cannot be opened. */
	protected boolean greyed(Row row) {
		return false;
	}

	/** True when the row shows an arrow and a click opens or folds it. */
	protected boolean expandable(Row row) {
		return row.node().kind() == SourceNode.Kind.FOLDER && !greyed(row);
	}

	/** False when no row can be expanded (the flat list), to drop the arrow column. */
	protected boolean arrowColumn() {
		return true;
	}

	protected Identifier icon(SourceNode node) {
		return SpeakerSprites.FOLDER;
	}

	protected String label(Row row) {
		return row.node().name();
	}

	/**
	 * Draws the right end of a row ({@code right} is the row's right edge) and returns where the name must stop.
	 */
	protected abstract int drawEnd(GuiGraphicsExtractor graphics, Font font, Row row, int right, int y, boolean hovered,
			int mouseX, int mouseY);

	/** True when {@code mouseX} is on one of the buttons {@link #drawEnd} drew. */
	protected abstract boolean overEnd(Row row, double mouseX);

	/** A click on one of those buttons; true when it did something. */
	protected abstract boolean clickEnd(Row row, double mouseX);

	/** A click on a row that cannot be expanded, outside its buttons; true when it did something. */
	protected boolean clickOther(Row row, boolean doubleClick) {
		return false;
	}

	/** A row past the tree's own rows (the library's status line). */
	protected void drawExtraRow(GuiGraphicsExtractor graphics, Font font, int left, int right, int y, boolean hovered,
			int mouseX, int mouseY) {
	}

	@Override
	protected final boolean clickRow(int index, double mouseX, boolean doubleClick) {
		List<Row> rows = rows();
		if (index >= rows.size()) {
			return false;
		}
		Row row = rows.get(index);
		if (row.loading() || !clickable(row)) {
			return false;
		}
		if (overEnd(row, mouseX)) {
			return clickEnd(row, mouseX);
		}
		if (expandable(row)) {
			toggle(row.node());
			return true;
		}
		return clickOther(row, doubleClick);
	}

	/** True when {@code mouseX} is on the button of width {@code w} at the right end of the rows. */
	protected final boolean overRight(double mouseX, int w) {
		return mouseX >= rowRight() - w && mouseX < rowRight();
	}

	@Override
	protected final void drawRow(GuiGraphicsExtractor graphics, Font font, int index, int left, int right, int y,
			boolean hovered, int mouseX, int mouseY) {
		List<Row> rows = rows();
		if (index >= rows.size()) {
			drawExtraRow(graphics, font, left, right, y, hovered, mouseX, mouseY);
			return;
		}
		Row row = rows.get(index);
		int x = left + 1 + row.depth() * INDENT;
		if (row.loading()) {
			String loading = Component.translatable(SpeakerSprites.KEY + "loading").getString();
			graphics.text(font, SpeakerSprites.fit(font, loading, right - x - ARROW_W), x + ARROW_W, y + 2,
					SpeakerSprites.MUTED, false);
			return;
		}
		boolean grey = greyed(row);
		if (hovered && clickable(row)) {
			graphics.fill(left, y, right, y + ROW_H, HOVER);
		}
		if (expandable(row)) {
			SpeakerSprites.drawArrow(graphics, x + 1, y + 3, row.expanded());
		}
		int iconX = arrowColumn() ? x + ARROW_W : x + 1;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon(row.node()), iconX, y + 1, SpeakerSprites.ICON,
				SpeakerSprites.ICON, grey ? 0.4F : 1.0F);
		int end = drawEnd(graphics, font, row, right, y, hovered, mouseX, mouseY);
		int nameX = iconX + SpeakerSprites.ICON + 2;
		graphics.text(font, SpeakerSprites.fit(font, label(row), end - nameX), nameX, y + 2,
				grey ? SpeakerSprites.ABSENT : SpeakerSprites.TEXT, !grey);
	}

	/** The centred message of an empty tree. */
	protected final void drawMessage(GuiGraphicsExtractor graphics, Font font, String key) {
		Component message = Component.translatable(SpeakerSprites.KEY + key);
		graphics.centeredText(font, SpeakerSprites.fit(font, message.getString(), width - 2 * PAD - 2),
				getX() + width / 2, innerTop() + 6, SpeakerSprites.MUTED);
	}
}
