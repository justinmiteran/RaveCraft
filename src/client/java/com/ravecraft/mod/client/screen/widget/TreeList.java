package com.ravecraft.mod.client.screen.widget;

import com.ravecraft.mod.client.library.OffThread;
import com.ravecraft.mod.core.library.LibraryTreeModel;
import com.ravecraft.mod.core.library.LibraryTreeModel.Row;
import com.ravecraft.mod.core.library.SourceNode;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * A {@link RowList} showing a {@link LibraryTreeModel}: the base of the library, the chosen folders and the folder
 * browser. Subclasses say how to list the roots and a folder's children; this class runs those listings on the IO
 * pool and applies them on the client thread, only if the tree was not reloaded meanwhile and is not closed.
 *
 * <p>Subclasses turn each model row into a {@link FolderRow} or a {@link FileRow} with its items ({@link #rowFor});
 * the {@code Loading…} rows are drawn here.
 */
public abstract class TreeList extends RowList {
	public static final int ROW_H = 11;

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

	/** The row standing for {@code row} of the model, never a loading one. */
	protected abstract LibraryRow rowFor(Row row);

	/** A row past the tree's own rows (the library's status line); {@code index} counts from the first of them. */
	protected ListRow extraRow(int index) {
		throw new IndexOutOfBoundsException(index);
	}

	@Override
	protected final ListRow row(int index) {
		List<Row> rows = rows();
		if (index >= rows.size()) {
			return extraRow(index - rows.size());
		}
		Row row = rows.get(index);
		return row.loading() ? LibraryRow.loading(row.depth()) : rowFor(row);
	}

	/** A folder row that opens and folds through this tree, listing its children the first time. */
	protected final FolderRow folder(Row row) {
		return new FolderRow(row.node(), row.depth(), row.expanded(), () -> toggle(row.node()));
	}

	/** The centred message of an empty tree. */
	protected final void drawMessage(GuiGraphicsExtractor graphics, Font font, String key) {
		Component message = Component.translatable(SpeakerSprites.KEY + key);
		graphics.centeredText(font, SpeakerSprites.fit(font, message.getString(), width - 2 * PAD - 2),
				getX() + width / 2, innerTop() + 6, SpeakerSprites.MUTED);
	}
}
