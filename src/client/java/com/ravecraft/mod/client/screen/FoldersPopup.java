package com.ravecraft.mod.client.screen;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.client.library.FolderPicker;
import com.ravecraft.mod.client.library.FolderPicker.PickResult;
import com.ravecraft.mod.client.library.LibraryFoldersStore;
import com.ravecraft.mod.client.screen.widget.FolderBrowser;
import com.ravecraft.mod.client.screen.widget.FolderTree;
import com.ravecraft.mod.client.screen.widget.SpeakerSprites;
import com.ravecraft.mod.core.library.LibraryFolders;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The {@code Folders…} popup over the speaker screen: the chosen library folders as a tree of their subfolders, where
 * a subfolder is excluded from the library on hover (see {@link FolderTree}), and below it either the actions ({@code Add a folder…} through the system dialog, {@code Built-in browser}), the wait for
 * the system dialog, or the in-game folder browser. Every change is saved and reported through {@code onChanged}.
 * The screen routes all input here while the popup is open.
 */
public final class FoldersPopup extends AbstractContainerEventHandler {
	public static final int WIDTH = 236;
	public static final int HEIGHT = 200;
	private static final int INNER_W = WIDTH - 14;
	private static final int LIST_Y = 17;
	/** The tree is shorter while the in-game browser shares the popup. */
	private static final int LIST_H = 9 * FolderTree.ROW_H + 4;
	private static final int LIST_H_BROWSING = 4 * FolderTree.ROW_H + 4;
	private static final int BUTTON_Y = HEIGHT - 18;
	private static final int BROWSER_Y = LIST_Y + LIST_H_BROWSING + 3 + 11 + 10;
	private static final int BUTTON_H = 14;
	private static final int DONE_W = 56;
	private static final int ACTION_W = 140;
	private static final int DIM = 0xA0000000;
	private static final int WARNING = 0xFF8A2A10;

	private enum Mode {
		ACTIONS, WAITING, BROWSER
	}

	private final SpeakerScreen parent;
	private final Runnable onChanged;
	private final FolderTree tree;
	private final FolderBrowser browser;
	private final Button addButton;
	private final Button browserButton;
	private final Button cancelButton;
	private final Button addThisButton;
	private final Button doneButton;
	private Mode mode = Mode.ACTIONS;
	private int left;
	private int top;
	private int screenWidth;
	private int screenHeight;
	private @Nullable Component message;
	private boolean warning;
	/** Bumped when a dialog request is started or abandoned: a dialog result for an older request is ignored. */
	private int pickRequest;
	private boolean closed;

	public FoldersPopup(SpeakerScreen parent, Runnable onChanged) {
		this.parent = parent;
		this.onChanged = onChanged;
		tree = new FolderTree(INNER_W, LIST_H, new FolderTree.Actions() {
			@Override
			public void remove(Path chosen) {
				FoldersPopup.this.remove(chosen);
			}

			@Override
			public void exclude(Path folder) {
				FoldersPopup.this.exclude(folder);
			}

			@Override
			public void include(Path folder) {
				save(LibraryFoldersStore.get().include(folder));
				setMessage(null, false);
			}
		});
		browser = new FolderBrowser(INNER_W, BUTTON_Y - 4 - BROWSER_Y);
		addButton = Button.builder(text("add"), b -> pickWithDialog()).size(ACTION_W, BUTTON_H).build();
		browserButton = Button.builder(text("browser"), b -> openBrowser()).size(ACTION_W, BUTTON_H).build();
		cancelButton = Button.builder(text("cancel"), b -> cancelPick()).size(ACTION_W / 2, BUTTON_H).build();
		addThisButton = Button.builder(text("add_this"), b -> addCurrent()).size(ACTION_W - 20, BUTTON_H).build();
		doneButton = Button.builder(text("done"), b -> parent.closeFolders()).size(DONE_W, BUTTON_H).build();
		tree.setFolders(LibraryFoldersStore.get());
	}

	private static Component text(String key, Object... args) {
		return Component.translatable(SpeakerSprites.KEY + "folders." + key, args);
	}

	/** Centres the popup on the screen and places its widgets (on opening and on every resize). */
	public void layout(int screenWidth, int screenHeight) {
		this.screenWidth = screenWidth;
		this.screenHeight = screenHeight;
		left = (screenWidth - WIDTH) / 2;
		top = (screenHeight - HEIGHT) / 2;
		tree.setPosition(left + 7, top + LIST_Y);
		browser.setPosition(left + 7, top + BROWSER_Y);
		int areaMid = top + (areaY() + BUTTON_Y - 4) / 2;
		addButton.setPosition(left + (WIDTH - ACTION_W) / 2, areaMid - BUTTON_H - 2);
		browserButton.setPosition(left + (WIDTH - ACTION_W) / 2, areaMid + 2);
		cancelButton.setPosition(left + (WIDTH - ACTION_W / 2) / 2, areaMid + 6);
		addThisButton.setPosition(left + 7, top + BUTTON_Y);
		doneButton.setPosition(left + WIDTH - 7 - DONE_W, top + BUTTON_Y);
	}

	/** Stops applying late results (dialog, disk listings) once the popup is gone. */
	public void close() {
		closed = true;
		pickRequest++;
		tree.close();
	}

	private int listHeight() {
		return mode == Mode.BROWSER ? LIST_H_BROWSING : LIST_H;
	}

	private int messageY() {
		return LIST_Y + listHeight() + 3;
	}

	private int areaY() {
		return messageY() + 11;
	}

	@Override
	public List<? extends GuiEventListener> children() {
		List<AbstractWidget> widgets = new ArrayList<>();
		widgets.add(tree);
		switch (mode) {
			case ACTIONS -> {
				widgets.add(addButton);
				widgets.add(browserButton);
			}
			case WAITING -> widgets.add(cancelButton);
			case BROWSER -> {
				widgets.add(browser);
				widgets.add(addThisButton);
			}
		}
		widgets.add(doneButton);
		return widgets;
	}

	// --- Actions ----------------------------------------------------------------------------------------------

	private void pickWithDialog() {
		setMode(Mode.WAITING);
		setMessage(null, false);
		int request = ++pickRequest;
		Path home = Path.of(System.getProperty("user.home"));
		FolderPicker.pick(text("dialog_title").getString(), home)
				.thenAccept(result -> Minecraft.getInstance().execute(() -> picked(request, result)));
	}

	private void picked(int request, PickResult result) {
		if (closed || request != pickRequest || mode != Mode.WAITING) {
			return;
		}
		switch (result) {
			case PickResult.Chosen(Path folder) -> {
				setMode(Mode.ACTIONS);
				add(folder);
			}
			case PickResult.Cancelled() -> setMode(Mode.ACTIONS);
			case PickResult.Unavailable(String reason) -> {
				RaveCraft.LOGGER.warn("System folder dialog unavailable ({}), using the in-game browser", reason);
				openBrowser();
				setMessage(text("unavailable"), true);
			}
		}
	}

	/** {@code Cancel} only stops waiting: the system dialog cannot be closed from here, its result is ignored. */
	private void cancelPick() {
		pickRequest++;
		setMode(Mode.ACTIONS);
	}

	private void openBrowser() {
		setMode(Mode.BROWSER);
		setMessage(null, false);
		browser.open(browser.current());
	}

	private void addCurrent() {
		Path folder = browser.current();
		if (folder != null) {
			add(folder);
		}
	}

	private void add(Path folder) {
		LibraryFolders.Added added = LibraryFoldersStore.get().add(folder);
		switch (added.result()) {
			case ADDED -> {
				save(added.folders());
				setMessage(text("added"), false);
			}
			case REPLACED_CHILDREN -> {
				save(added.folders());
				setMessage(text("replaced"), false);
			}
			case DUPLICATE -> setMessage(text("duplicate"), true);
			case INSIDE_EXISTING -> setMessage(text("inside_existing"), true);
			case FULL -> setMessage(text("full", LibraryFolders.MAX), true);
			case OUTSIDE_CHOSEN -> {
				// only exclusions are refused this way
			}
		}
	}

	private void exclude(Path folder) {
		LibraryFolders.Added added = LibraryFoldersStore.get().exclude(folder);
		switch (added.result()) {
			case ADDED -> {
				save(added.folders());
				setMessage(text("excluded"), false);
			}
			case REPLACED_CHILDREN -> {
				save(added.folders());
				setMessage(text("excluded_replaced"), false);
			}
			case DUPLICATE -> setMessage(text("already_excluded"), true);
			case INSIDE_EXISTING -> setMessage(text("inside_excluded"), true);
			case FULL -> setMessage(text("full", LibraryFolders.MAX), true);
			case OUTSIDE_CHOSEN -> {
				// the tree only offers subfolders of chosen folders
			}
		}
	}

	private void remove(Path folder) {
		save(LibraryFoldersStore.get().remove(folder));
		setMessage(null, false);
	}

	/** Saves the lists (a tiny JSON file, written on the client thread) and reloads what depends on them. */
	private void save(LibraryFolders folders) {
		LibraryFoldersStore.set(folders);
		tree.setFolders(folders);
		onChanged.run();
	}

	/** Switches the lower part; focus is dropped so a key cannot press a button that is no longer shown. */
	private void setMode(Mode mode) {
		this.mode = mode;
		setFocused(null);
		tree.setHeight(listHeight());
		layout(screenWidth, screenHeight);
	}

	private void setMessage(@Nullable Component message, boolean warning) {
		this.message = message;
		this.warning = warning;
	}

	// --- Drawing ----------------------------------------------------------------------------------------------

	/** Draws the popup above everything the screen drew, with the screen darkened behind it. */
	public void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Font font = Minecraft.getInstance().font;
		graphics.nextStratum();
		graphics.fill(0, 0, screenWidth, screenHeight, DIM);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SpeakerSprites.WINDOW, left, top, WIDTH, HEIGHT);
		graphics.text(font, text("title"), left + 8, top + 6, SpeakerSprites.LABEL, false);
		Component line = message != null ? message : mode == Mode.ACTIONS ? text("hint") : null;
		if (line != null) {
			graphics.text(font, SpeakerSprites.fit(font, line.getString(), INNER_W), left + 8, top + messageY(),
					warning ? WARNING : SpeakerSprites.LABEL, false);
		}
		if (mode == Mode.WAITING) {
			graphics.textWithWordWrap(font, text("waiting"), left + 12, top + areaY() + 10, WIDTH - 24,
					SpeakerSprites.LABEL, false);
		} else if (mode == Mode.BROWSER) {
			Path current = browser.current();
			String where = current == null ? text("drives").getString()
					: text("add_to", breadcrumb(font, current, INNER_W - font.width(text("add_to", "").getString())))
					.getString();
			graphics.text(font, SpeakerSprites.fit(font, where, INNER_W), left + 8, top + areaY(), SpeakerSprites.LABEL,
					false);
			addThisButton.active = current != null;
		}
		for (GuiEventListener child : children()) {
			((AbstractWidget) child).extractRenderState(graphics, mouseX, mouseY, a);
		}
	}

	/** {@code C: > Users > Alex}, dropping the leading folders ({@code … >}) when it is wider than {@code maxWidth}. */
	private static String breadcrumb(Font font, Path path, int maxWidth) {
		List<String> parts = new ArrayList<>();
		Path root = path.getRoot();
		if (root != null) {
			String drive = root.toString().replaceAll("[\\\\/]+$", "");
			parts.add(drive.isEmpty() ? root.toString() : drive);
		}
		for (Path name : path) {
			parts.add(name.toString());
		}
		String separator = " > ";
		String text = String.join(separator, parts);
		while (font.width(text) > maxWidth && parts.size() > 1) {
			parts.remove(0);
			text = "…" + separator + String.join(separator, parts);
		}
		return text;
	}
}
