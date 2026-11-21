package com.ravecraft.mod.client.screen;

import com.ravecraft.mod.block.PortableSpeakerBlock;
import com.ravecraft.mod.blockentity.PortableSpeakerBlockEntity;
import com.ravecraft.mod.component.SpeakerData;
import com.ravecraft.mod.registry.ModDataComponents;
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
import com.ravecraft.mod.core.speaker.SpeakerLayout;
import com.ravecraft.mod.core.speaker.SpeakerLayout.Rect;
import com.ravecraft.mod.network.payload.SpeakerCommandPayload.Command;
import com.ravecraft.mod.network.payload.SpeakerStatePayload;
import com.ravecraft.mod.network.payload.SpeakerWatchPayload;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import org.jspecify.annotations.Nullable;

/**
 * The speaker's screen, a vanilla-style window: the player band on the speaker's fabric, the controls, the library
 * on the left (source tabs, search and tree) and the shared queue on the right. Its size follows the screen's (see
 * {@link SpeakerLayout}). It watches the speaker's state while open and closes when the server says it is gone or out of reach.
 */
public final class SpeakerScreen extends Screen {
	private static final int BAND_Y = 17;
	private static final int CONTROLS_Y = 65;
	/** Odd, like every icon button, so the 9 px icons sit exactly in the middle of the button's face. */
	private static final int SEARCH_H = 15;
	private static final int CHOOSE_H = 14;

	/** How far around the player the speaker's block is looked for, to tint the band with its fabric. */
	private static final int BLOCK_SEARCH_RADIUS = 8;

	private final UUID speakerId;
	/** The speaker's block, found once by its id when the screen opens; null when not found. */
	private final @Nullable BlockPos block;
	/** The fabric colour of the carried stack of this speaker when there is no block, else null. */
	private final @Nullable Integer carriedColour;
	private SpeakerLayout layout = SpeakerLayout.of(0, 0);
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

	/** The screen of speaker {@code speakerId}, titled {@code title}. */
	public SpeakerScreen(UUID speakerId, Component title) {
		super(title);
		this.speakerId = speakerId;
		this.block = findBlock(speakerId);
		this.carriedColour = block == null ? findCarriedColour(speakerId) : null;
		// A state kept from an earlier opening may be stale: wait for the one the server sends on watching.
		SpeakerStates.forget(speakerId);
	}

	/** The speaker this screen controls. */
	public UUID speakerId() {
		return speakerId;
	}

	/** The block of speaker {@code id} near the player, or null. Scans once, when the screen opens. */
	private static @Nullable BlockPos findBlock(UUID id) {
		Minecraft client = Minecraft.getInstance();
		Player player = client.player;
		if (client.level == null || player == null) {
			return null;
		}
		BlockPos centre = player.blockPosition();
		for (BlockPos pos : BlockPos.withinClippedManhattan(centre, BLOCK_SEARCH_RADIUS, BLOCK_SEARCH_RADIUS, BLOCK_SEARCH_RADIUS)) {
			if (client.level.getBlockEntity(pos) instanceof PortableSpeakerBlockEntity speaker && speaker.data().id().equals(id)) {
				return pos.immutable();
			}
		}
		return null;
	}

	/** The fabric colour of the speaker in the local player's inventory, or null when it is not carried. */
	private static @Nullable Integer findCarriedColour(UUID id) {
		Player player = Minecraft.getInstance().player;
		if (player == null) {
			return null;
		}
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			SpeakerData data = stack.get(ModDataComponents.SPEAKER);
			if (data != null && data.id().equals(id)) {
				DyedItemColor dye = stack.get(DataComponents.DYED_COLOR);
				return ARGB.opaque(dye != null ? dye.rgb() : PortableSpeakerBlock.DEFAULT_COLOR);
			}
		}
		return null;
	}

	/** The speaker's block entity, when it is placed, loaded here and still holds the id. */
	private @Nullable PortableSpeakerBlockEntity placedSpeaker() {
		if (block == null || minecraft == null || minecraft.level == null) {
			return null;
		}
		return minecraft.level.getBlockEntity(block) instanceof PortableSpeakerBlockEntity speaker
				&& speaker.data().id().equals(speakerId) ? speaker : null;
	}

	/** The fabric colour of the speaker's block, or the undyed colour when it is not found. */
	private int fabricColour() {
		PortableSpeakerBlockEntity speaker = placedSpeaker();
		if (speaker != null) {
			return ARGB.opaque(speaker.fabricColor());
		}
		if (carriedColour != null) {
			return carriedColour;
		}
		return ARGB.opaque(PortableSpeakerBlock.DEFAULT_COLOR);
	}

	@Override
	protected void init() {
		layout = SpeakerLayout.of(width, height);
		left = (width - layout.width()) / 2;
		top = (height - layout.height()) / 2;
		addRenderableWidget(new PlayerBand(left + 7, top + BAND_Y, layout.bandWidth(), speakerId, this::fabricColour));
		controls = new ControlRow(left, top + CONTROLS_Y, layout.controlsWidth(), speakerId, this::addRenderableWidget);
		initLibrary();
		Rect q = layout.queue();
		queue = addRenderableWidget(new QueuePanel(left + q.x(), top + q.y(), q.w(), q.h(), speakerId));
		int buttonW = (q.w() - 4) / 2;
		clearButton = addRenderableWidget(Button.builder(Component.translatable(SpeakerSprites.KEY + "clear"),
						b -> SpeakerStates.send(speakerId, Command.CLEAR, Optional.empty()))
				.bounds(left + q.x(), top + layout.buttonsY(), buttonW, layout.buttonsHeight()).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
				.bounds(left + q.x() + q.w() - buttonW, top + layout.buttonsY(), buttonW, layout.buttonsHeight()).build());
		shownState = null;
		if (folders != null) {
			folders.layout(width, height);
		}
		if (ClientPlayNetworking.canSend(SpeakerWatchPayload.TYPE)) {
			ClientPlayNetworking.send(new SpeakerWatchPayload(speakerId, true));
		}
		tick();
	}

	/**
	 * The library column: source tabs on the window's edge, the search field, the tree, the {@code Choose a
	 * folder…} button shown over an empty tree, and {@code Folders…} at the bottom.
	 */
	private void initLibrary() {
		List<MusicSource> sources = List.of(new LocalFolderSource(() -> LibraryFoldersStore.get().paths(),
				() -> LibraryFoldersStore.get().excluded()),
				new UnavailableSource("usb"), new UnavailableSource("stream"));
		Rect lib = layout.library();
		int libX = left + lib.x();
		int libY = top + lib.y();
		int treeY = libY + SEARCH_H + 2;
		addRenderableWidget(new SourceTabs(left, libY, sources, 0));
		search = addRenderableWidget(new EditBox(font, libX, libY, lib.w() - 2 * SEARCH_H - 4, SEARCH_H,
				Component.translatable(SpeakerSprites.KEY + "search")));
		search.setHint(Component.translatable(SpeakerSprites.KEY + "search"));
		search.setValue(filter);
		if (library != null) {
			library.close(); // the screen is being rebuilt (resize)
		}
		library = addRenderableWidget(new LibraryTree(libX, treeY, lib.w(), lib.h() - (treeY - libY), sources.get(0),
				speakerId));
		library.setFilter(filter);
		library.setFlat(flatView);
		addRenderableWidget(new IconButton(libX + lib.w() - 2 * SEARCH_H - 2, libY, SEARCH_H,
				SpeakerSprites.REFRESH, Component.translatable(SpeakerSprites.KEY + "refresh"), this::reloadLibrary));
		viewButton = addRenderableWidget(new IconButton(libX + lib.w() - SEARCH_H, libY, SEARCH_H,
				viewIcon(), viewLabel(), this::toggleView));
		search.setResponder(text -> {
			filter = text;
			library.setFilter(text);
		});
		chooseButton = addRenderableWidget(Button.builder(Component.translatable(SpeakerSprites.KEY + "choose_folder"),
						b -> openFolders())
				.bounds(libX + 6, treeY + 20, lib.w() - 12, CHOOSE_H).build());
		chooseButton.visible = false;
		addRenderableWidget(Button.builder(Component.translatable(SpeakerSprites.KEY + "folders"), b -> openFolders())
				.bounds(libX, top + layout.buttonsY(), lib.w(), layout.buttonsHeight()).build());
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
		Rect lib = layout.library();
		return new ScreenRectangle(left + lib.x(), top + lib.y(), lib.w(), lib.h());
	}

	@Override
	public void removed() {
		super.removed();
		closeFolders();
		library.close();
		if (ClientPlayNetworking.canSend(SpeakerWatchPayload.TYPE)) {
			ClientPlayNetworking.send(new SpeakerWatchPayload(speakerId, false));
		}
	}

	@Override
	public void tick() {
		SpeakerStatePayload state = SpeakerStates.get(speakerId).orElse(null);
		if (state != shownState) {
			shownState = state;
			queue.show(state);
		}
		controls.update(state, state != null && state.powered());
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
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SpeakerSprites.WINDOW, left, top, layout.width(),
				layout.height());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		// Under the popup, nothing is hovered.
		int screenX = folders != null ? -1 : mouseX;
		int screenY = folders != null ? -1 : mouseY;
		Rect lib = layout.library();
		Rect q = layout.queue();
		graphics.text(font, SpeakerSprites.fit(font, title.getString(), layout.titleWidth()), left + 8, top + 6,
				SpeakerSprites.LABEL, false);
		graphics.text(font, Component.translatable(SpeakerSprites.KEY + "library"), left + lib.x() + 1,
				top + lib.y() - 10, SpeakerSprites.LABEL, false);
		graphics.text(font, Component.translatable(SpeakerSprites.KEY + "queue"), left + q.x() + 1,
				top + layout.queueLabelY(), SpeakerSprites.LABEL, false);
		super.extractRenderState(graphics, screenX, screenY, a);
		if (folders != null) {
			folders.extract(graphics, mouseX, mouseY, a);
		}
	}
}
