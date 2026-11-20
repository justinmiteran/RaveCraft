package com.ravecraft.mod.client.screen.widget;

import com.ravecraft.mod.client.audio.LocalPlayback;
import com.ravecraft.mod.client.library.LibraryIndexStore;
import com.ravecraft.mod.client.library.OffThread;
import com.ravecraft.mod.client.screen.SpeakerStates;
import com.ravecraft.mod.core.library.LibraryIndex;
import com.ravecraft.mod.core.library.LibraryTreeModel;
import com.ravecraft.mod.core.library.LibraryTreeModel.Row;
import com.ravecraft.mod.core.library.MusicSource;
import com.ravecraft.mod.core.library.SourceNode;
import com.ravecraft.mod.core.queue.SpeakerQueue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The library as an expandable tree in a recessed panel: the source's roots (the chosen folders), then the
 * children of each expanded folder. Every source call may touch the disk, so it runs on the IO pool and
 * its result is applied on the client thread, only if the tree has not been reloaded since. Rows show the
 * indentation, an expand arrow, the type icon, the name, the tracks under each folder (subfolders included) and a
 * {@code +} that queues the node's tracks on the speaker. The flat view lists every track and playlist instead,
 * without folders, each with its folder as a tooltip. Both read the {@link LibraryIndex} kept by
 * {@link LibraryIndexStore}, shown at once and brought up to date in the background; while it is, a last row says
 * so. The state and the search filter live in {@link LibraryTreeModel}.
 */
public final class LibraryTree extends TreeList {
	private static final int ROW_H = 11;
	private static final int ADD_W = SpeakerSprites.ICON + 1;
	private static final int SEPARATOR = 0xFF6A6A6A;

	private final MusicSource source;
	private final BlockPos pos;
	/** The index the rows were last built from, and its version then. */
	private @org.jspecify.annotations.Nullable LibraryIndex index;
	private int indexVersion;

	public LibraryTree(int x, int y, int width, int height, MusicSource source, BlockPos pos) {
		super(x, y, width, height, ROW_H, Component.translatable(SpeakerSprites.KEY + "library"));
		this.source = source;
		this.pos = pos;
		loadRoots();
		LibraryIndexStore.open(source);
		syncIndex();
	}

	/**
	 * Lists everything again while the current tree and list stay shown (Refresh, or after the chosen folders
	 * changed): the roots and the open folders are read again, the index is walked again in the background.
	 */
	public void reload() {
		reloadAll();
		LibraryIndexStore.refresh(source);
		syncIndex();
	}

	/** Switches between the folder tree and the flat list. */
	public void setFlat(boolean flat) {
		if (flat != model.flat()) {
			model.setFlat(flat);
			resetScroll();
		}
	}

	@Override
	protected List<SourceNode> listRoots() throws IOException {
		return source.roots();
	}

	@Override
	protected List<SourceNode> listChildren(String folderId) throws IOException {
		return source.children(SourceNode.folder(folderId));
	}

	/** Hands the index's entries to the flat list when the index changed since the last look. */
	private LibraryIndex syncIndex() {
		LibraryIndex current = LibraryIndexStore.get(source);
		if (current != index || current.version() != indexVersion) {
			index = current;
			indexVersion = current.version();
			model.setFlatNodes(current.entries());
		}
		return current;
	}

	/** Filters the loaded rows by name; never reads the disk. */
	public void setFilter(String filter) {
		model.setFilter(filter);
		resetScroll();
	}

	/** True once the roots are known and there are none: the screen then offers to choose a folder. */
	public boolean isEmptyLibrary() {
		return model.isEmpty();
	}

	/** An empty library lets clicks through to the screen's {@code Choose a folder…} button drawn over it. */
	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return !model.isEmpty() && super.isMouseOver(mouseX, mouseY);
	}

	private void add(SourceNode node) {
		if (node.kind() == SourceNode.Kind.FOLDER) {
			// A folder brings its subfolders' tracks too, as found by the library walk.
			List<Path> indexed =
					LibraryIndexStore.get(source).tracksUnder(node.id(), SpeakerQueue.roomLeft(queued()));
			if (!indexed.isEmpty()) {
				LocalPlayback.add(pos, indexed);
				return;
			}
		}
		OffThread.run(() -> source.tracksOf(node), tracks -> {
			// Cap to the room left now, so no entry is registered for an id the server would drop.
			int room = Math.min(tracks.size(), SpeakerQueue.roomLeft(queued()));
			if (room > 0) {
				LocalPlayback.add(pos, tracks.subList(0, room));
			}
		}, () -> {
		});
	}

	/** Entries in the queue as last reported by the server. */
	private int queued() {
		return SpeakerStates.get(pos).map(state -> state.entries().size()).orElse(0);
	}

	private boolean full() {
		return SpeakerQueue.roomLeft(queued()) == 0;
	}

	// --- Layout -----------------------------------------------------------------------------------------------

	private List<Row> rows() {
		syncIndex();
		return model.rows();
	}

	/** The last row: the index being brought up to date, or, in the flat view, that it stopped at its limit. */
	private boolean footerShown() {
		return LibraryIndexStore.refreshing(source) || model.flat() && syncIndex().truncated();
	}

	/** No row at all for an empty library, so the panel shows its message instead. */
	@Override
	protected int rowCount() {
		return model.isEmpty() ? 0 : rows().size() + (footerShown() ? 1 : 0);
	}

	private static boolean addable(Row row) {
		return !row.loading() && !row.node().missing();
	}

	private boolean overAdd(double mouseX) {
		return mouseX >= rowRight() - ADD_W && mouseX < rowRight();
	}

	// --- Input ------------------------------------------------------------------------------------------------

	@Override
	protected boolean clickRow(int index, double mouseX, boolean doubleClick) {
		List<Row> rows = rows();
		if (index >= rows.size() || !addable(rows.get(index))) {
			return false;
		}
		SourceNode node = rows.get(index).node();
		if (overAdd(mouseX)) {
			if (full()) {
				return false;
			}
			add(node);
			return true;
		}
		if (node.kind() == SourceNode.Kind.FOLDER) {
			toggle(node);
			return true;
		}
		// A track or playlist (a file, not a folder) is queued by a double click, like its + button.
		if (doubleClick && !full()) {
			add(node);
			return true;
		}
		return false;
	}

	// --- Drawing ----------------------------------------------------------------------------------------------

	@Override
	protected void drawEmpty(GuiGraphicsExtractor graphics, Font font) {
		if (model.isEmpty()) {
			// The screen puts its "Choose a folder…" button under this message.
			drawMessage(graphics, font, "no_folders");
		}
	}

	@Override
	protected void drawRow(GuiGraphicsExtractor graphics, Font font, int index, int left, int right, int rowY,
			boolean hovered, int mouseX, int mouseY) {
		List<Row> rows = rows();
		if (index == rows.size()) {
			drawFooter(graphics, font, left, right, rowY, hovered, mouseX, mouseY);
		} else {
			drawNode(graphics, font, rows.get(index), left, right, rowY, hovered, mouseX, mouseY);
		}
	}

	private void drawNode(GuiGraphicsExtractor graphics, Font font, Row row, int left, int right, int rowY,
			boolean hovered, int mouseX, int mouseY) {
		int x = indent(row, left);
		int textY = rowY + 2;
		if (row.loading()) {
			drawLoading(graphics, font, x, right, rowY);
			return;
		}
		SourceNode node = row.node();
		boolean missing = node.missing();
		if (hovered && !missing) {
			graphics.fill(left, rowY, right, rowY + ROW_H, HOVER);
		}
		if (node.kind() == SourceNode.Kind.FOLDER && !missing) {
			SpeakerSprites.drawArrow(graphics, x + 1, rowY + 3, row.expanded());
		}
		// The flat list has no folders, so no arrow column either.
		int iconX = model.flat() ? x + 1 : x + ARROW_W;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon(node), iconX, rowY + 1, SpeakerSprites.ICON,
				SpeakerSprites.ICON, missing ? 0.4F : 1.0F);
		int nameX = iconX + SpeakerSprites.ICON + 2;
		int end = right;
		if (missing) {
			if (hovered) {
				graphics.setTooltipForNextFrame(font, Component.translatable(SpeakerSprites.KEY + "missing"), mouseX, mouseY);
			}
		} else {
			int addX = right - ADD_W;
			boolean overAdd = hovered && overAdd(mouseX);
			if (overAdd) {
				SpeakerSprites.cellHover(graphics, addX - 1, rowY, right, rowY + ROW_H);
				Component tip = full()
						? Component.translatable(SpeakerSprites.KEY + "queue_full", SpeakerQueue.MAX_ENTRIES)
						: Component.translatable(SpeakerSprites.KEY + "add");
				graphics.setTooltipForNextFrame(font, tip, mouseX, mouseY);
			} else if (hovered) {
				graphics.setTooltipForNextFrame(font, rowTooltip(node), Optional.empty(), mouseX, mouseY);
			}
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SpeakerSprites.PLUS, addX, rowY + 1, SpeakerSprites.ICON,
					SpeakerSprites.ICON, full() ? 0.4F : 1.0F);
			end = addX - 2;
			if (node.kind() == SourceNode.Kind.FOLDER) {
				String count = countLabel(node);
				end -= font.width(count);
				graphics.text(font, count, end, textY, SpeakerSprites.MUTED, false);
				end -= 3;
			}
		}
		String name = SpeakerSprites.fit(font, node.name(), end - nameX);
		graphics.text(font, name, nameX, textY, missing ? SpeakerSprites.ABSENT : SpeakerSprites.TEXT, !missing);
	}

	/**
	 * The tooltip of a hovered row, in both views: the full name (rows cut long names short), then the track count of a
	 * folder, or in the flat list the folder a track or playlist is in.
	 */
	private List<Component> rowTooltip(SourceNode node) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal(node.name()));
		if (node.kind() == SourceNode.Kind.FOLDER) {
			lines.add(Component.translatable(SpeakerSprites.KEY + "folders.tracks", countLabel(node))
					.withStyle(ChatFormatting.GRAY));
		} else if (model.flat()) {
			String folder = source.parentName(node);
			if (!folder.isEmpty()) {
				lines.add(Component.translatable(SpeakerSprites.KEY + "in_folder", folder).withStyle(ChatFormatting.GRAY));
			}
		}
		return lines;
	}

	/** The tracks under a folder; {@code …} while the index has not seen it yet. */
	private String countLabel(SourceNode folder) {
		return LibraryIndexStore.countLabel(source.id(), folder.id()).orElse("…");
	}

	private void drawFooter(GuiGraphicsExtractor graphics, Font font, int left, int right, int rowY, boolean hovered,
			int mouseX, int mouseY) {
		LibraryIndex current = syncIndex();
		boolean refreshing = LibraryIndexStore.refreshing(source);
		if (hovered && current.truncated()) {
			graphics.setTooltipForNextFrame(font,
					Component.translatable(SpeakerSprites.KEY + "flat.limit", LibraryIndex.LIMIT), mouseX, mouseY);
		}
		graphics.fill(left + 1, rowY, right - 1, rowY + 1, SEPARATOR);
		int textY = rowY + 2;
		String found = current.size() + (current.truncated() ? "+" : "");
		int countX = right - 2 - font.width(found);
		if (refreshing) {
			String key = current.complete() ? "flat.updating" : "flat.searching";
			String label = Component.translatable(SpeakerSprites.KEY + key).getString();
			graphics.text(font, SpeakerSprites.fit(font, label, countX - left - 6), left + 2, textY,
					SpeakerSprites.MUTED, true);
		}
		graphics.text(font, found, countX, textY, SpeakerSprites.MUTED, true);
	}

	private static Identifier icon(SourceNode node) {
		return switch (node.kind()) {
			case FOLDER -> SpeakerSprites.FOLDER;
			case PLAYLIST -> SpeakerSprites.PLAYLIST;
			case TRACK -> SpeakerSprites.TRACK;
		};
	}
}
