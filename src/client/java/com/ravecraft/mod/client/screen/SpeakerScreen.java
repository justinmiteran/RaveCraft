package com.ravecraft.mod.client.screen;

import com.ravecraft.mod.block.PortableSpeakerBlock;
import com.ravecraft.mod.blockentity.PortableSpeakerBlockEntity;
import com.ravecraft.mod.client.screen.widget.ControlRow;
import com.ravecraft.mod.client.screen.widget.PlayerBand;
import com.ravecraft.mod.client.screen.widget.QueuePanel;
import com.ravecraft.mod.client.screen.widget.SpeakerSprites;
import com.ravecraft.mod.network.payload.SpeakerCommandPayload.Command;
import com.ravecraft.mod.network.payload.SpeakerStatePayload;
import com.ravecraft.mod.network.payload.SpeakerWatchPayload;
import com.ravecraft.mod.registry.ModBlocks;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The speaker's screen, a vanilla-style window: the player band on the speaker's fabric, the controls, the library
 * on the left (filled by the library tree) and the shared queue on the right. It watches the speaker's state while
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

	private final BlockPos pos;
	private int left;
	private int top;
	private ControlRow controls;
	private QueuePanel queue;
	private Button clearButton;
	private @Nullable SpeakerStatePayload shownState;

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
		queue = addRenderableWidget(new QueuePanel(left + QUEUE_X, top + PANEL_Y, PANEL_W, PANEL_H, pos));
		clearButton = addRenderableWidget(Button.builder(Component.translatable(SpeakerSprites.KEY + "clear"),
						b -> SpeakerStates.send(pos, Command.CLEAR, Optional.empty()))
				.bounds(left + QUEUE_X, top + BOTTOM_Y, 56, BOTTOM_H).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
				.bounds(left + QUEUE_X + PANEL_W - 56, top + BOTTOM_Y, 56, BOTTOM_H).build());
		shownState = null;
		if (ClientPlayNetworking.canSend(SpeakerWatchPayload.TYPE)) {
			ClientPlayNetworking.send(new SpeakerWatchPayload(pos, true));
		}
		tick();
	}

	/** Where the library goes, in screen coordinates: an empty recessed panel until the library tree fills it. */
	public ScreenRectangle libraryArea() {
		return new ScreenRectangle(left + 7, top + PANEL_Y, PANEL_W, PANEL_H);
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
		SpeakerStatePayload state = SpeakerStates.get(pos).orElse(null);
		if (state != shownState) {
			shownState = state;
			queue.show(state);
		}
		controls.update(state, state != null ? state.powered() : blockPowered());
		clearButton.active = state != null && !state.entries().isEmpty();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (super.keyPressed(event)) {
			return true;
		}
		if (minecraft.options.keyInventory.matches(event)) {
			onClose();
			return true;
		}
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SpeakerSprites.WINDOW, left, top, WIDTH, HEIGHT);
		ScreenRectangle library = libraryArea();
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SpeakerSprites.INSET, library.left(), library.top(),
				library.width(), library.height());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		graphics.text(font, SpeakerSprites.fit(font, title.getString(), WIDTH - 16), left + 8, top + 6, SpeakerSprites.LABEL, false);
		graphics.text(font, Component.translatable(SpeakerSprites.KEY + "library"), left + 8, top + PANEL_Y - 10,
				SpeakerSprites.LABEL, false);
		graphics.text(font, Component.translatable(SpeakerSprites.KEY + "queue"), left + QUEUE_X + 1, top + PANEL_Y - 10,
				SpeakerSprites.LABEL, false);
		super.extractRenderState(graphics, mouseX, mouseY, a);
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
