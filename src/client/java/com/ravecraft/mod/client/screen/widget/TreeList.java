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
 */
public abstract class TreeList extends RowList {
	protected static final int INDENT = 8;
	protected static final int ARROW_W = 7;

	protected final LibraryTreeModel model = new LibraryTreeModel();
	private boolean closed;

	protected TreeList(int x, int y, int width, int height, int rowH, Component label) {
		super(x, y, width, height, rowH, label);
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

	/** Left edge of a row's content: its arrow column, indented by depth. */
	protected static int indent(Row row, int left) {
		return left + 1 + row.depth() * INDENT;
	}

	/** The {@code Loading…} line shown under a folder while its children are listed. */
	protected static void drawLoading(GuiGraphicsExtractor graphics, Font font, int x, int right, int y) {
		String loading = Component.translatable(SpeakerSprites.KEY + "loading").getString();
		graphics.text(font, SpeakerSprites.fit(font, loading, right - x - ARROW_W), x + ARROW_W, y + 2,
				SpeakerSprites.MUTED, false);
	}

	/** The centred message of an empty tree. */
	protected final void drawMessage(GuiGraphicsExtractor graphics, Font font, String key) {
		Component message = Component.translatable(SpeakerSprites.KEY + key);
		graphics.centeredText(font, SpeakerSprites.fit(font, message.getString(), width - 2 * PAD - 2),
				getX() + width / 2, innerTop() + 6, SpeakerSprites.MUTED);
	}
}
