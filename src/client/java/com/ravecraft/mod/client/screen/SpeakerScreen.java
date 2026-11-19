package com.ravecraft.mod.client.screen;

import com.ravecraft.mod.block.PortableSpeakerBlock;
import com.ravecraft.mod.blockentity.PortableSpeakerBlockEntity;
import com.ravecraft.mod.client.library.LibraryFoldersStore;
import com.ravecraft.mod.client.screen.widget.ControlRow;
import com.ravecraft.mod.client.screen.widget.LibraryTree;
import com.ravecraft.mod.client.screen.widget.PlayerBand;
import com.ravecraft.mod.client.screen.widget.QueuePanel;
import com.ravecraft.mod.client.screen.widget.SourceTabs;
import com.ravecraft.mod.client.screen.widget.IconButton;
import com.ravecraft.mod.client.screen.widget.SpeakerSprites;
import com.ravecraft.mod.core.library.LocalFolderSource;
import com.ravecraft.mod.core.library.MusicSource;
import com.ravecraft.mod.core.library.UnavailableSource;
import com.ravecraft.mod.network.payload.SpeakerCommandPayload.Command;
import com.ravecraft.mod.network.payload.SpeakerStatePayload;
import com.ravecraft.mod.network.payload.SpeakerWatchPayload;
import com.ravecraft.mod.registry.ModBlocks;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The speaker's screen, a vanilla-style window: the player band on the speaker's fabric, the controls, the library
 * on the left (source tabs, search and tree) and the shared queue on the right. It watches the speaker's state while
 * open and closes when the speaker is gone.
 */
public final class SpeakerScreen extends Screen {
	public static final int WIDTH = 256;
	public static final int HEIGHT = 216;
	private static final int PANEL_Y = 98;
	private static final int PANEL_W = 116;
	private static final int PANEL_H = 96;
	private static final int QUEUE_X = WIDTH - 7 - PANEL_W;
	private static final int BOTTOM_Y = 198;
	private static final int BOTTOM_H = 14;
	private static final int SEARCH_H = 12;
	private static final int TREE_Y = PANEL_Y + SEARCH_H + 2;
	private static final int CHOOSE_H = 14;

	private final BlockPos pos;
	private int left;
	private int top;
	private ControlRow controls;
	private QueuePanel queue;
	private Button clearButton;
	private LibraryTree library;
	private EditBox search;
	private Button chooseButton;
	/** Whether the library shows the flat list instead of the tree; kept for the game session. */
	private static boolean flatView;
	private IconButton viewButton;
	/** The search text, kept when the screen is rebuilt (resize). */
	private String filter = "";
	private @Nullable SpeakerStatePayload shownState;
	/** The {@code Folders…} popup while it is open; it then gets all the input. */
	private @Nullable FoldersPopup folders;

	public SpeakerScreen(BlockPos pos) {
		super(titleOf(pos));
		this.pos = pos.immutable();
		// A state kept from an earlier opening may be stale: wait for the one the server sends on watching.
		SpeakerStates.forget(this.pos);
	}

	/** The item's custom name when the speaker has one, otherwise the block's name. */
	private static Component titleOf(BlockPos pos) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null && level.getBlockEntity(pos) instanceof PortableSpeakerBlockEntity speaker
				&& speaker.customName() != null) {
			return speaker.customName();
		}
		return ModBlocks.PORTABLE_SPEAKER.getName();
	}

	@Override
	protected void init() {
		left = (width - WIDTH) / 2;
		top = (height - HEIGHT) / 2;
		addRenderableWidget(new PlayerBand(left + 7, top + 17, WIDTH - 14, pos));
		controls = new ControlRow(left, top + 65, pos, this::addRenderableWidget);
		initLibrary();
		queue = addRenderableWidget(new QueuePanel(left + QUEUE_X, top + PANEL_Y, PANEL_W, PANEL_H, pos));
		clearButton = addRenderableWidget(Button.builder(Component.translatable(SpeakerSprites.KEY + "clear"),
						b -> SpeakerStates.send(pos, Command.CLEAR, Optional.empty()))
				.bounds(left + QUEUE_X, top + BOTTOM_Y, 56, BOTTOM_H).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
				.bounds(left + QUEUE_X + PANEL_W - 56, top + BOTTOM_Y, 56, BOTTOM_H).build());
		shownState = null;
		if (folders != null) {
			folders.layout(width, height);
		}
		if (ClientPlayNetworking.canSend(SpeakerWatchPayload.TYPE)) {
			ClientPlayNetworking.send(new SpeakerWatchPayload(pos, true));
		}
		tick();
	}

	/**
	 * The library column: source tabs on the window's edge, the search field, the tree, the {@code Choose a
	 * folder…} button shown over an empty tree, and {@code Folders…} at the bottom.
	 */
	private void initLibrary() {
		List<MusicSource> sources = List.of(new LocalFolderSource(() -> LibraryFoldersStore.get().paths()),
				new UnavailableSource("usb"), new UnavailableSource("stream"));
		addRenderableWidget(new SourceTabs(left, top + PANEL_Y, sources, 0));
		search = addRenderableWidget(new EditBox(font, left + 7, top + PANEL_Y, PANEL_W - 2 * SEARCH_H - 4, SEARCH_H,
				Component.translatable(SpeakerSprites.KEY + "search")));
		search.setHint(Component.translatable(SpeakerSprites.KEY + "search"));
		search.setValue(filter);
		if (library != null) {
			library.close(); // the screen is being rebuilt (resize)
		}
		library = addRenderableWidget(new LibraryTree(left + 7, top + TREE_Y, PANEL_W, PANEL_H - (TREE_Y - PANEL_Y),
				sources.get(0), pos));
		library.setFilter(filter);
		library.setFlat(flatView);
		addRenderableWidget(new IconButton(left + 7 + PANEL_W - 2 * SEARCH_H - 2, top + PANEL_Y, SEARCH_H,
				SpeakerSprites.REFRESH, Component.translatable(SpeakerSprites.KEY + "refresh"), this::reloadLibrary));
		viewButton = addRenderableWidget(new IconButton(left + 7 + PANEL_W - SEARCH_H, top + PANEL_Y, SEARCH_H,
				viewIcon(), viewLabel(), this::toggleView));
		search.setResponder(text -> {
			filter = text;
			library.setFilter(text);
		});
		chooseButton = addRenderableWidget(Button.builder(Component.translatable(SpeakerSprites.KEY + "choose_folder"),
						b -> openFolders())
				.bounds(left + 13, top + TREE_Y + 20, PANEL_W - 12, CHOOSE_H).build());
		chooseButton.visible = false;
		addRenderableWidget(Button.builder(Component.translatable(SpeakerSprites.KEY + "folders"), b -> openFolders())
				.bounds(left + 7, top + BOTTOM_Y, PANEL_W, BOTTOM_H).build());
	}

	/** The view button shows the current view: the tree or the flat list. */
	private void toggleView() {
		flatView = !flatView;
		library.setFlat(flatView);
		viewButton.show(viewIcon(), viewLabel());
	}

	private static Identifier viewIcon() {
		return flatView ? SpeakerSprites.VIEW_LIST : SpeakerSprites.VIEW_TREE;
	}

	private static Component viewLabel() {
		return Component.translatable(SpeakerSprites.KEY + (flatView ? "view.flat" : "view.tree"));
	}

	/** Opens the {@code Folders…} popup, which reloads the library whenever the chosen folders change. */
	public void openFolders() {
		if (folders == null) {
			clearFocus();
			folders = new FoldersPopup(this, this::reloadLibrary);
			folders.layout(width, height);
		}
	}

	/** Closes the {@code Folders…} popup, back to the speaker screen. */
	public void closeFolders() {
		if (folders != null) {
			folders.close();
			folders = null;
		}
	}

	/** Lists the chosen folders again, after they changed. */
	public void reloadLibrary() {
		library.reload();
	}

	/** The library column (search field and tree), in screen coordinates. */
	public ScreenRectangle libraryArea() {
		return new ScreenRectangle(left + 7, top + PANEL_Y, PANEL_W, PANEL_H);
	}

	@Override
	public void removed() {
		super.removed();
		closeFolders();
		library.close();
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
		SpeakerStatePayload state = SpeakerStates.get(pos).orElse(null);
		if (state != shownState) {
			shownState = state;
			queue.show(state);
		}
		controls.update(state, state != null ? state.powered() : blockPowered());
		clearButton.active = state != null && !state.entries().isEmpty();
		chooseButton.visible = library.isEmptyLibrary();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (folders != null) {
			// Escape closes the popup only; nothing reaches the screen underneath.
			if (event.isEscape()) {
				closeFolders();
			} else {
				folders.keyPressed(event);
			}
			return true;
		}
		if (super.keyPressed(event)) {
			return true;
		}
		if (minecraft.options.keyInventory.matches(event) && !search.canConsumeInput()) {
			onClose();
			return true;
		}
		return false;
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (folders != null) {
			folders.keyReleased(event);
			return true;
		}
		return super.keyReleased(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return folders != null || super.charTyped(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (folders != null) {
			folders.mouseClicked(event, doubleClick);
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (folders != null) {
			folders.mouseReleased(event);
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (folders != null) {
			folders.mouseDragged(event, dx, dy);
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (folders != null) {
			folders.mouseScrolled(x, y, scrollX, scrollY);
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SpeakerSprites.WINDOW, left, top, WIDTH, HEIGHT);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		// Under the popup, nothing is hovered.
		int screenX = folders != null ? -1 : mouseX;
		int screenY = folders != null ? -1 : mouseY;
		graphics.text(font, SpeakerSprites.fit(font, title.getString(), WIDTH - 16), left + 8, top + 6, SpeakerSprites.LABEL, false);
		graphics.text(font, Component.translatable(SpeakerSprites.KEY + "library"), left + 8, top + PANEL_Y - 10,
				SpeakerSprites.LABEL, false);
		graphics.text(font, Component.translatable(SpeakerSprites.KEY + "queue"), left + QUEUE_X + 1, top + PANEL_Y - 10,
				SpeakerSprites.LABEL, false);
		super.extractRenderState(graphics, screenX, screenY, a);
		if (folders != null) {
			folders.extract(graphics, mouseX, mouseY, a);
		}
	}

	private boolean speakerPresent() {
		return minecraft.level != null && minecraft.level.getBlockState(pos).is(ModBlocks.PORTABLE_SPEAKER);
	}

	private boolean blockPowered() {
		if (minecraft.level == null) {
			return false;
		}
		BlockState state = minecraft.level.getBlockState(pos);
		return state.is(ModBlocks.PORTABLE_SPEAKER) && state.getValue(PortableSpeakerBlock.POWERED);
	}
}
