package com.ravecraft.mod.client.screen;

import com.ravecraft.mod.block.PortableSpeakerBlock;
import com.ravecraft.mod.client.audio.LocalPlayback;
import com.ravecraft.mod.core.library.LibraryEntry;
import com.ravecraft.mod.core.library.LibraryEntry.Kind;
import com.ravecraft.mod.core.library.MusicLibrary;
import com.ravecraft.mod.network.payload.SpeakerCommandPayload.Command;
import com.ravecraft.mod.network.payload.SpeakerStatePayload;
import com.ravecraft.mod.network.payload.SpeakerStatePayload.StateEntry;
import com.ravecraft.mod.network.payload.SpeakerWatchPayload;
import com.ravecraft.mod.registry.ModBlocks;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The speaker's screen: the player's drives and folders on the left, the speaker's shared queue on the right, the
 * player controls at the bottom. The queue shown is the one the server sends while this screen is open.
 */
public final class SpeakerBrowserScreen extends Screen {
	private static final String KEY = "screen.ravecraft.speaker_browser.";
	private static final int HEADER = 44;
	private static final int FOOTER = 74;
	private static final int MARGIN = 8;
	private static final int ROW_HEIGHT = 14;
	private static final int REMOVE_WIDTH = 10;
	private static final int FOLDER_COLOR = 0xFFE8C66A;
	private static final int PLAYLIST_COLOR = 0xFF8CC8FF;
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private static final int MUTED_COLOR = 0xFFA0A0A0;
	private static final int ABSENT_COLOR = 0xFF808080;
	private static final int WARNING_COLOR = 0xFFFFB050;
	private static final int ACCENT_COLOR = 0xFFFFD65A;
	private static final int CURRENT_FILL = 0x60806020;
	private static final int REMOVE_COLOR = 0xFFE06060;

	/** Remembered between openings so the player comes back to the same folder. */
	private static @Nullable Path lastFolder;

	private final BlockPos pos;
	private @Nullable Path folder;
	private List<LibraryEntry> entries = List.of();
	private int generation;
	private RowList list;
	private QueueList queueList;
	private @Nullable SpeakerStatePayload shownState;
	private Button backButton;
	private Button addButton;
	private Button playButton;
	private Button nextButton;
	private Button clearButton;

	public SpeakerBrowserScreen(BlockPos pos) {
		super(Component.translatable(KEY + "title"));
		this.pos = pos.immutable();
		this.folder = lastFolder;
		// A state kept from an earlier opening may be stale: wait for the one the server sends on watching.
		SpeakerStates.forget(this.pos);
	}

	@Override
	protected void init() {
		int half = width / 2;
		int listHeight = height - HEADER - FOOTER;
		list = addRenderableWidget(new RowList(minecraft, half - MARGIN - 4, listHeight, HEADER));
		list.setX(MARGIN);
		queueList = addRenderableWidget(new QueueList(minecraft, half - MARGIN - 4, listHeight, HEADER));
		queueList.setX(half + 4);
		shownState = null;

		backButton = addRenderableWidget(Button.builder(Component.translatable(KEY + "back"), b -> goUp())
				.bounds(MARGIN, 6, 60, 20).build());
		addButton = addRenderableWidget(Button.builder(Component.translatable(KEY + "add"), b -> add())
				.bounds(half - 4 - 100, height - FOOTER + 4, 100, 20).build());
		int bottom = height - 26;
		playButton = addRenderableWidget(Button.builder(Component.translatable(KEY + "play"), b -> togglePlay())
				.bounds(half - 154, bottom, 100, 20).build());
		nextButton = addRenderableWidget(Button.builder(Component.translatable(KEY + "next"), b -> send(Command.NEXT, null))
				.bounds(half - 50, bottom, 100, 20).build());
		clearButton = addRenderableWidget(Button.builder(Component.translatable(KEY + "clear"), b -> send(Command.CLEAR, null))
				.bounds(half + 54, bottom, 100, 20).build());
		show(folder);
		if (ClientPlayNetworking.canSend(SpeakerWatchPayload.TYPE)) {
			ClientPlayNetworking.send(new SpeakerWatchPayload(pos, true));
		}
		tick();
	}

	@Override
	public void removed() {
		super.removed();
		if (ClientPlayNetworking.canSend(SpeakerWatchPayload.TYPE)) {
			ClientPlayNetworking.send(new SpeakerWatchPayload(pos, false));
		}
	}

	@Override
	public void tick() {
		if (!speakerPresent()) {
			onClose();
			return;
		}
		Optional<SpeakerStatePayload> state = SpeakerStates.get(pos);
		if (state.orElse(null) != shownState) {
			shownState = state.orElse(null);
			queueList.show(shownState);
		}
		boolean queued = shownState != null && !shownState.entries().isEmpty();
		boolean playing = shownState != null && shownState.playing();
		playButton.setMessage(Component.translatable(KEY + (playing ? "pause" : "play")));
		playButton.active = queued && (playing || speakerPowered());
		nextButton.active = shownState != null && shownState.current().isPresent();
		clearButton.active = queued;
		addButton.active = canAdd();
		backButton.active = folder != null;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(font, title, width / 2, 8, TEXT_COLOR);
		String where = folder == null ? Component.translatable(KEY + "roots").getString() : folder.toString();
		graphics.text(font, ellipsizeStart(where, width / 2 - MARGIN - 8), MARGIN + 2, HEADER - 12, MUTED_COLOR);
		int count = shownState == null ? 0 : shownState.entries().size();
		graphics.text(font, Component.translatable(KEY + "queue", count), width / 2 + 6, HEADER - 12, MUTED_COLOR);
		graphics.centeredText(font, statusLine(), width / 2, height - 44, statusColor());
	}

	private Component statusLine() {
		if (!speakerPowered()) {
			return Component.translatable(KEY + "off_hint");
		}
		Optional<StateEntry> current = currentEntry();
		if (current.isEmpty()) {
			return Component.translatable(KEY + (shownState == null || shownState.entries().isEmpty() ? "queue_empty" : "nothing_playing"));
		}
		String time = formatTime(SpeakerStates.positionMs(pos));
		String key = shownState.playing() ? "now_playing" : "paused";
		return Component.translatable(KEY + key, current.get().title(), current.get().ownerName(), time);
	}

	private int statusColor() {
		return speakerPowered() ? TEXT_COLOR : WARNING_COLOR;
	}

	private Optional<StateEntry> currentEntry() {
		if (shownState == null || shownState.current().isEmpty()) {
			return Optional.empty();
		}
		UUID current = shownState.current().get();
		return shownState.entries().stream().filter(e -> e.entryId().equals(current)).findFirst();
	}

	private static String formatTime(long ms) {
		long seconds = ms / 1000;
		return String.format("%d:%02d", seconds / 60, seconds % 60);
	}

	private String ellipsizeStart(String text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text;
		}
		return "..." + font.plainSubstrByWidth(text, maxWidth - font.width("..."), true);
	}

	private boolean speakerPresent() {
		return minecraft.level != null && minecraft.level.getBlockState(pos).is(ModBlocks.PORTABLE_SPEAKER);
	}

	private boolean speakerPowered() {
		if (minecraft.level == null) {
			return false;
		}
		BlockState state = minecraft.level.getBlockState(pos);
		return state.is(ModBlocks.PORTABLE_SPEAKER) && state.getValue(PortableSpeakerBlock.POWERED);
	}

	private boolean canAdd() {
		LibraryEntry selected = selectedEntry();
		if (selected != null) {
			return selected.kind() != Kind.ROOT;
		}
		return entries.stream().anyMatch(e -> e.kind() == Kind.TRACK);
	}

	private @Nullable LibraryEntry selectedEntry() {
		Row selected = list.getSelected();
		return selected instanceof FileEntry file ? file.entry : null;
	}

	// --- Navigation -------------------------------------------------------------------------------------------

	private void open(LibraryEntry entry) {
		show(entry.path());
	}

	private void goUp() {
		if (folder == null) {
			return;
		}
		Path parent = folder.getParent();
		boolean isRoot = MusicLibrary.roots().stream().anyMatch(r -> r.path().equals(folder));
		show(isRoot ? null : parent);
	}

	/** Lists {@code target} (or the roots when null) off the render thread. */
	private void show(@Nullable Path target) {
		folder = target;
		lastFolder = target;
		entries = List.of();
		list.showMessage(Component.translatable(KEY + "loading"));
		int request = ++generation;
		runAsync(() -> target == null ? MusicLibrary.roots() : MusicLibrary.list(target), result -> {
			if (request != generation) {
				return;
			}
			entries = result;
			if (result.isEmpty()) {
				list.showMessage(Component.translatable(KEY + "empty"));
			} else {
				list.showEntries(result);
			}
		}, () -> {
			if (request == generation) {
				list.showMessage(Component.translatable(KEY + "error"));
			}
		});
	}

	// --- Queue and player -------------------------------------------------------------------------------------

	/** Adds the selected track, folder or playlist to the speaker's queue; with nothing selected, every listed track. */
	private void add() {
		LibraryEntry selected = selectedEntry();
		if (selected == null) {
			LocalPlayback.add(pos, entries.stream().filter(e -> e.kind() == Kind.TRACK).map(LibraryEntry::path).toList());
		} else if (selected.kind() == Kind.TRACK) {
			LocalPlayback.add(pos, List.of(selected.path()));
		} else if (selected.kind() != Kind.ROOT) {
			runAsync(() -> MusicLibrary.tracksOf(selected), tracks -> LocalPlayback.add(pos, tracks), () -> {
			});
		}
	}

	private void togglePlay() {
		send(shownState != null && shownState.playing() ? Command.PAUSE : Command.PLAY, null);
	}

	private void send(Command command, @Nullable UUID entryId) {
		SpeakerStates.send(pos, command, Optional.ofNullable(entryId));
	}

	/** Runs file access on the IO pool and applies the result only while this screen is still open. */
	private <T> void runAsync(Callable<T> work, Consumer<T> onResult, Runnable onError) {
		CompletableFuture.supplyAsync(() -> {
			try {
				return work.call();
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}, Util.ioPool()).whenCompleteAsync((result, error) -> {
			if (error == null) {
				onResult.accept(result);
			} else {
				onError.run();
			}
		}, screenExecutor);
	}

	// --- Browser list -----------------------------------------------------------------------------------------

	private final class RowList extends ObjectSelectionList<Row> {
		RowList(Minecraft minecraft, int width, int height, int y) {
			super(minecraft, width, height, y, ROW_HEIGHT);
		}

		@Override
		public int getRowWidth() {
			return width - 28;
		}

		void showMessage(Component message) {
			setSelected(null);
			replaceEntries(List.of(new MessageEntry(message)));
			setScrollAmount(0);
		}

		void showEntries(List<LibraryEntry> listed) {
			setSelected(null);
			replaceEntries(listed.stream().<Row>map(FileEntry::new).toList());
			setScrollAmount(0);
		}
	}

	private abstract static class Row extends ObjectSelectionList.Entry<Row> {
	}

	private final class MessageEntry extends Row {
		private final Component message;

		MessageEntry(Component message) {
			this.message = message;
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
			graphics.centeredText(font, message, getX() + getWidth() / 2, getContentYMiddle() - font.lineHeight / 2, MUTED_COLOR);
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			return false;
		}

		@Override
		public Component getNarration() {
			return message;
		}
	}

	private final class FileEntry extends Row {
		private final LibraryEntry entry;
		private final String label;

		FileEntry(LibraryEntry entry) {
			this.entry = entry;
			this.label = entry.isBrowsable() ? entry.name() + "/" : entry.name();
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
			int color = switch (entry.kind()) {
				case ROOT, FOLDER -> FOLDER_COLOR;
				case PLAYLIST -> PLAYLIST_COLOR;
				case TRACK -> TEXT_COLOR;
			};
			String shown = font.plainSubstrByWidth(label, getContentWidth() - 4);
			graphics.text(font, shown, getContentX() + 2, getContentYMiddle() - font.lineHeight / 2, color);
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			if (entry.isBrowsable()) {
				open(entry);
			} else if (doubleClick) {
				list.setSelected(this);
				add();
			}
			return true;
		}

		@Override
		public Component getNarration() {
			return Component.literal(label);
		}
	}

	// --- Queue list -------------------------------------------------------------------------------------------

	private final class QueueList extends ObjectSelectionList<QueueRow> {
		QueueList(Minecraft minecraft, int width, int height, int y) {
			super(minecraft, width, height, y, ROW_HEIGHT);
		}

		@Override
		public int getRowWidth() {
			return width - 28;
		}

		/** Rebuilds the rows from a new state, keeping the scroll position. */
		void show(@Nullable SpeakerStatePayload state) {
			double scroll = scrollAmount();
			setSelected(null);
			List<QueueRow> rows = new ArrayList<>();
			if (state != null) {
				for (StateEntry entry : state.entries()) {
					rows.add(new QueueRow(entry, state.current().filter(entry.entryId()::equals).isPresent()));
				}
			}
			replaceEntries(rows);
			setScrollAmount(scroll);
		}
	}

	private final class QueueRow extends ObjectSelectionList.Entry<QueueRow> {
		private final StateEntry entry;
		private final boolean current;

		QueueRow(StateEntry entry, boolean current) {
			this.entry = entry;
			this.current = current;
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
			int x = getContentX();
			int right = getContentRight();
			int textY = getContentYMiddle() - font.lineHeight / 2;
			if (current) {
				graphics.fill(x, getContentY(), right, getContentY() + getContentHeight(), CURRENT_FILL);
				graphics.text(font, ">", x + 2, textY, ACCENT_COLOR);
			}
			int color = entry.ownerOnline() ? TEXT_COLOR : ABSENT_COLOR;
			Component owner = entry.ownerOnline() ? Component.literal(entry.ownerName())
					: Component.translatable(KEY + "absent", entry.ownerName());
			int ownerWidth = Math.min(font.width(owner), getContentWidth() / 3);
			int removeX = right - REMOVE_WIDTH;
			int ownerX = removeX - 6 - ownerWidth;
			String title = font.plainSubstrByWidth(entry.title(), ownerX - 6 - (x + 12));
			graphics.text(font, title, x + 12, textY, color);
			graphics.text(font, font.plainSubstrByWidth(owner.getString(), ownerWidth), ownerX, textY,
					entry.ownerOnline() ? MUTED_COLOR : ABSENT_COLOR);
			boolean overRemove = hovered && mouseX >= removeX;
			graphics.text(font, "x", removeX + 2, textY, overRemove ? REMOVE_COLOR : MUTED_COLOR);
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			if (event.x() >= getContentRight() - REMOVE_WIDTH) {
				send(Command.REMOVE, entry.entryId());
			}
			return true;
		}

		@Override
		public Component getNarration() {
			return Component.literal(entry.title() + ", " + entry.ownerName());
		}
	}
}
