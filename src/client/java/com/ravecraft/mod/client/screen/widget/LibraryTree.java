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
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

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
	private static final int SEPARATOR = 0xFF6A6A6A;

	private final MusicSource source;
	private final BlockPos pos;
	/** The index the rows were last built from, and its version then. */
	private @org.jspecify.annotations.Nullable LibraryIndex index;
	private int indexVersion;

	public LibraryTree(int x, int y, int width, int height, MusicSource source, BlockPos pos) {
		super(x, y, width, height, Component.translatable(SpeakerSprites.KEY + "library"));
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

	@Override
	protected List<Row> rows() {
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

	@Override
	protected void drawEmpty(GuiGraphicsExtractor graphics, Font font) {
		if (model.isEmpty()) {
			// The screen puts its "Choose a folder…" button under this message.
			drawMessage(graphics, font, "no_folders");
		}
	}

	@Override
	protected LibraryRow rowFor(Row row) {
		SourceNode node = row.node();
		LibraryRow line = switch (node.kind()) {
			case FOLDER -> folder(row);
			// A track or playlist is queued by a double click, like its + button.
			case PLAYLIST, TRACK -> new FileRow(node, row.depth()).onDoubleClick(() -> {
				if (!full()) {
					add(node);
				}
			});
		};
		if (model.flat()) {
			line.noArrowColumn();
		}
		if (node.missing()) {
			return line.greyed(true).inert()
					.tooltip(List.of(Component.translatable(SpeakerSprites.KEY + "missing")));
		}
		RowItem add = RowItem.button(SpeakerSprites.PLUS, Component.translatable(SpeakerSprites.KEY + "add"),
				() -> add(node)).enabledIf(!full(),
						Component.translatable(SpeakerSprites.KEY + "queue_full", SpeakerQueue.MAX_ENTRIES));
		RowItem count = node.kind() == SourceNode.Kind.FOLDER ? RowItem.text(countLabel(node)) : null;
		return line.item(add).item(count).tooltip(rowTooltip(node));
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

	/** The status line: the index being brought up to date, or how many entries the flat list holds. */
	@Override
	protected ListRow extraRow(int index) {
		LibraryIndex current = syncIndex();
		boolean refreshing = LibraryIndexStore.refreshing(source);
		return new ListRow() {
			@Override
			public boolean highlights() {
				return false;
			}

			@Override
			public List<Component> tooltip() {
				return current.truncated()
						? List.of(Component.translatable(SpeakerSprites.KEY + "flat.limit", LibraryIndex.LIMIT))
						: List.of();
			}

			@Override
			public void draw(GuiGraphicsExtractor graphics, Font font, int left, int end, int y) {
				graphics.fill(left + 1, y, end - 1, y + 1, SEPARATOR);
				String found = current.size() + (current.truncated() ? "+" : "");
				int countX = end - 2 - font.width(found);
				if (refreshing) {
					String key = current.complete() ? "flat.updating" : "flat.searching";
					String label = Component.translatable(SpeakerSprites.KEY + key).getString();
					graphics.text(font, SpeakerSprites.fit(font, label, countX - left - 6), left + 2, y + 2,
							SpeakerSprites.MUTED, true);
				}
				graphics.text(font, found, countX, y + 2, SpeakerSprites.MUTED, true);
			}
		};
	}
}
