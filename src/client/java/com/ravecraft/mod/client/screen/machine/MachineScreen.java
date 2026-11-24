package com.ravecraft.mod.client.screen.machine;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.core.machine.MachineLayout;
import com.ravecraft.mod.core.machine.ProgressColors;
import com.ravecraft.mod.menu.MachineMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * The screen of any machine, laid out like a furnace: the inputs in a row, the energy slot below with a redstone
 * bolt between them that empties like the furnace flame, the progress arrow and the output on the right. The arrow
 * is plain, in the colour of the item being made, read from {@code assets/ravecraft/machine_colors.json}. Each
 * machine has its own background showing only the inputs it uses, {@code textures/gui/container/<machine>.png}. The
 * positions are those of {@link MachineLayout}, shared with {@link MachineMenu}.
 */
public class MachineScreen extends AbstractRecipeBookScreen<MachineMenu> {
	private static final Identifier PROGRESS_SPRITE = RaveCraft.id("container/machine/progress");
	private static final Identifier ENERGY_SPRITE = RaveCraft.id("container/machine/energy");
	private static final int TEXTURE_SIZE = 256;
	private static final int BOOK_BUTTON_X = 20;
	private static final int BOOK_BUTTON_Y = -49;

	private final Identifier texture;
	private final ProgressColors colors;

	/**
	 * A machine screen.
	 *
	 * @param menu the machine menu
	 * @param inventory the player's inventory
	 * @param title the machine's name
	 */
	public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
		super(menu, new MachineRecipeBookComponent(menu), inventory, title);
		texture = RaveCraft.id("textures/gui/container/" + menu.kind().machine() + ".png");
		colors = MachineColorsLoader.load(Minecraft.getInstance().getResourceManager());
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

	/** Draws the background, then the arrow filled by the progress and the bolt filled from below by the energy. */
	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, leftPos, topPos, 0, 0, imageWidth, imageHeight,
				TEXTURE_SIZE, TEXTURE_SIZE);
		int arrow = Mth.ceil(menu.progress() * MachineLayout.ARROW_WIDTH);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PROGRESS_SPRITE, MachineLayout.ARROW_WIDTH,
				MachineLayout.ARROW_HEIGHT, 0, 0, leftPos + MachineLayout.ARROW_X, topPos + MachineLayout.ARROW_Y, arrow,
				MachineLayout.ARROW_HEIGHT, arrowColor());
		int bolt = Mth.ceil(menu.energy() * MachineLayout.BOLT_SIZE);
		int top = MachineLayout.BOLT_SIZE - bolt;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ENERGY_SPRITE, MachineLayout.BOLT_SIZE,
				MachineLayout.BOLT_SIZE, 0, top, leftPos + MachineLayout.BOLT_X, topPos + MachineLayout.BOLT_Y + top,
				MachineLayout.BOLT_SIZE, bolt);
	}

	/** The colour of the item being made, white when none is listed or nothing is made. */
	private int arrowColor() {
		return menu.result()
				.map(item -> colors.colorOf(BuiltInRegistries.ITEM.getKey(item).toString()))
				.orElse(ProgressColors.WHITE);
	}
}
