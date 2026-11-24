package com.ravecraft.mod.client.compat.rei;

import com.ravecraft.mod.client.screen.machine.MachineScreen;
import com.ravecraft.mod.compat.rei.MachineDisplay;
import com.ravecraft.mod.recipe.MachineKind;
import com.ravecraft.mod.registry.ModRecipes;
import me.shedaniel.math.Point;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.screen.ClickArea;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandlerRegistry;
import me.shedaniel.rei.api.common.util.EntryStacks;

/**
 * Shows the machine recipes in REI, when REI is installed: one page per machine, the machine as its workstation, the
 * screen arrow opening the page and the "+" button filling the machine. Loaded only through the {@code rei_client}
 * entrypoint, so nothing here runs without REI.
 */
public final class RaveCraftReiClientPlugin implements REIClientPlugin {
	@Override
	public void registerCategories(CategoryRegistry registry) {
		for (MachineKind kind : ModRecipes.kinds()) {
			registry.add(new MachineDisplayCategory(kind));
			registry.addWorkstations(MachineDisplay.category(kind), EntryStacks.of(kind.machineItem()));
		}
	}

	@Override
	public void registerScreens(ScreenRegistry registry) {
		registry.registerClickArea(MachineScreen.class, RaveCraftReiClientPlugin::arrow);
	}

	@Override
	public void registerTransferHandlers(TransferHandlerRegistry registry) {
		for (MachineKind kind : ModRecipes.kinds()) {
			registry.register(new MachineTransferHandler(kind));
		}
	}

	/** The screen arrow, opening the page of the machine the screen belongs to. */
	private static ClickArea.Result arrow(ClickArea.ClickAreaContext<MachineScreen> context) {
		Point mouse = context.getMousePosition();
		MachineScreen screen = context.getScreen();
		return screen.arrowArea().containsPoint(mouse.x, mouse.y)
				? ClickArea.Result.success().category(MachineDisplay.category(screen.getMenu().kind()))
				: ClickArea.Result.fail();
	}
}
