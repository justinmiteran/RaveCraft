package com.ravecraft.mod.client.screen.machine;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.menu.MachineMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * The screen of any machine, laid out like a furnace: the three inputs in a row, the energy slot below with its
 * redstone gauge, the progress arrow and the output on the right. The positions are constants, shared with the slot
 * positions of {@link MachineMenu}.
 */
public class MachineScreen extends AbstractRecipeBookScreen<MachineMenu> {
	private static final Identifier TEXTURE = RaveCraft.id("textures/gui/container/machine.png");
	private static final Identifier PROGRESS_SPRITE = RaveCraft.id("container/machine/progress");
	private static final Identifier ENERGY_SPRITE = RaveCraft.id("container/machine/energy");
	private static final int TEXTURE_SIZE = 256;
	private static final int ARROW_X = 90;
	private static final int ARROW_Y = 34;
	private static final int ARROW_WIDTH = 22;
	private static final int ARROW_HEIGHT = 16;
	private static final int GAUGE_X = 67;
	private static final int GAUGE_Y = 53;
	private static final int GAUGE_WIDTH = 5;
	private static final int GAUGE_HEIGHT = 16;
	private static final int BOOK_BUTTON_X = 20;
	private static final int BOOK_BUTTON_Y = -49;

	/**
	 * A machine screen.
	 *
	 * @param menu the machine menu
	 * @param inventory the player's inventory
	 * @param title the machine's name
	 */
	public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
		super(menu, new MachineRecipeBookComponent(menu), inventory, title);
	}

	@Override
	public void init() {
		super.init();
		titleLabelX = (imageWidth - font.width(title)) / 2;
	}

	@Override
	protected ScreenPosition getRecipeBookButtonPosition() {
		return new ScreenPosition(leftPos + BOOK_BUTTON_X, height / 2 + BOOK_BUTTON_Y);
	}

	/** Draws the background, then the arrow filled by the progress and the gauge filled from below by the energy. */
	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight,
				TEXTURE_SIZE, TEXTURE_SIZE);
		int arrow = Mth.ceil(menu.progress() * ARROW_WIDTH);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PROGRESS_SPRITE, ARROW_WIDTH, ARROW_HEIGHT, 0, 0,
				leftPos + ARROW_X, topPos + ARROW_Y, arrow, ARROW_HEIGHT);
		int gauge = Mth.ceil(menu.energy() * GAUGE_HEIGHT);
		int top = GAUGE_HEIGHT - gauge;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ENERGY_SPRITE, GAUGE_WIDTH, GAUGE_HEIGHT, 0, top,
				leftPos + GAUGE_X, topPos + GAUGE_Y + top, GAUGE_WIDTH, gauge);
	}
}
