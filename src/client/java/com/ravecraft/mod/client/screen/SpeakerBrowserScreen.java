package com.ravecraft.mod.client.screen;

import com.ravecraft.mod.block.PortableSpeakerBlock;
import com.ravecraft.mod.client.audio.LocalPlayback;
import com.ravecraft.mod.core.library.AudioFiles;
import com.ravecraft.mod.core.library.LibraryEntry;
import com.ravecraft.mod.core.library.LibraryEntry.Kind;
import com.ravecraft.mod.core.library.MusicLibrary;
import com.ravecraft.mod.registry.ModBlocks;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
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

/** Browses the player's drives and folders and plays music through the speaker at {@code pos}. */
public final class SpeakerBrowserScreen extends Screen {
	private static final String KEY = "screen.ravecraft.speaker_browser.";
	private static final int HEADER = 34;
	private static final int FOOTER = 58;
	private static final int ROW_HEIGHT = 14;
	private static final int FOLDER_COLOR = 0xFFE8C66A;
	private static final int PLAYLIST_COLOR = 0xFF8CC8FF;
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private static final int MUTED_COLOR = 0xFFA0A0A0;
	private static final int WARNING_COLOR = 0xFFFFB050;

	/** Remembered between openings so the player comes back to the same folder. */
	private static @Nullable Path lastFolder;

	private final BlockPos pos;
	private @Nullable Path folder;
	private List<LibraryEntry> entries = List.of();
	private int generation;
	private RowList list;
	private Button backButton;
	private Button playButton;
	private Button stopButton;

	public SpeakerBrowserScreen(BlockPos pos) {
		super(Component.translatable(KEY + "title"));
		this.pos = pos.immutable();
		this.folder = lastFolder;
	}

	@Override
	protected void init() {
		list = addRenderableWidget(new RowList(minecraft, width, height - HEADER - FOOTER, HEADER));
		backButton = addRenderableWidget(Button.builder(Component.translatable(KEY + "back"), b -> goUp())
				.bounds(8, 6, 60, 20).build());
		int bottom = height - 26;
		playButton = addRenderableWidget(Button.builder(Component.translatable(KEY + "play"), b -> play())
				.bounds(width / 2 - 104, bottom, 100, 20).build());
		stopButton = addRenderableWidget(Button.builder(Component.translatable(KEY + "stop"), b -> LocalPlayback.stop(pos))
				.bounds(width / 2 + 4, bottom, 100, 20).build());
		show(folder);
	}

	@Override
	public void tick() {
		if (!speakerPresent()) {
			onClose();
			return;
		}
		boolean powered = speakerPowered();
		playButton.active = powered && canPlay();
		stopButton.active = LocalPlayback.nowPlaying(pos).isPresent();
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
		graphics.centeredText(font, ellipsizeStart(where, width - 160), width / 2, 20, MUTED_COLOR);

		int status = height - FOOTER + 8;
		if (!speakerPowered()) {
			graphics.centeredText(font, Component.translatable(KEY + "off_hint"), width / 2, status, WARNING_COLOR);
		} else {
			Component line = LocalPlayback.nowPlaying(pos)
					.map(track -> Component.translatable(KEY + "now_playing", AudioFiles.baseName(track)))
					.orElse(Component.translatable(KEY + "nothing_playing"));
			graphics.centeredText(font, line, width / 2, status, TEXT_COLOR);
		}
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

	private boolean canPlay() {
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

	// --- Playback ---------------------------------------------------------------------------------------------

	private void play() {
		if (!speakerPowered()) {
			return;
		}
		LibraryEntry selected = selectedEntry();
		if (selected == null) {
			LocalPlayback.play(pos, tracksIn(entries, null));
		} else if (selected.kind() == Kind.TRACK) {
			LocalPlayback.play(pos, tracksIn(entries, selected));
		} else if (selected.kind() != Kind.ROOT) {
			runAsync(() -> MusicLibrary.tracksOf(selected), tracks -> LocalPlayback.play(pos, tracks), () -> {
			});
		}
	}

	/** The listed tracks, starting from {@code first} when given. */
	private static List<Path> tracksIn(List<LibraryEntry> listed, @Nullable LibraryEntry first) {
		List<Path> tracks = new ArrayList<>();
		boolean started = first == null;
		for (LibraryEntry entry : listed) {
			started |= entry.equals(first);
			if (started && entry.kind() == Kind.TRACK) {
				tracks.add(entry.path());
			}
		}
		return tracks;
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

	// --- List widget ------------------------------------------------------------------------------------------

	private final class RowList extends ObjectSelectionList<Row> {
		RowList(Minecraft minecraft, int width, int height, int y) {
			super(minecraft, width, height, y, ROW_HEIGHT);
		}

		@Override
		public int getRowWidth() {
			return Math.min(360, width - 40);
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
			} else if (doubleClick && speakerPowered()) {
				list.setSelected(this);
				play();
			}
			return true;
		}

		@Override
		public Component getNarration() {
			return Component.literal(label);
		}
	}
}
