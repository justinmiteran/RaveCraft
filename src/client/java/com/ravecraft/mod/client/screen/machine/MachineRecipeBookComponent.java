package com.ravecraft.mod.client.screen.machine;

import com.ravecraft.mod.blockentity.MachineBlockEntity;
import com.ravecraft.mod.menu.MachineMenu;
import com.ravecraft.mod.recipe.MachineRecipeDisplay;
import java.util.List;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.recipebook.GhostSlots;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.display.RecipeDisplay;

/**
 * The recipe book of a machine, with one tab for its kind: it shows the machine recipes, and a clicked recipe it
 * cannot place appears as ghost items in the inputs, the energy slot (when empty) and the output.
 */
public class MachineRecipeBookComponent extends RecipeBookComponent<MachineMenu> {
	private static final WidgetSprites FILTER_SPRITES = new WidgetSprites(
			Identifier.withDefaultNamespace("recipe_book/furnace_filter_enabled"),
			Identifier.withDefaultNamespace("recipe_book/furnace_filter_disabled"),
			Identifier.withDefaultNamespace("recipe_book/furnace_filter_enabled_highlighted"),
			Identifier.withDefaultNamespace("recipe_book/furnace_filter_disabled_highlighted"));
	private static final Component FILTER_NAME = Component.translatable("gui.ravecraft.recipebook.toggle_recipes.machine");

	/**
	 * A recipe book for a machine menu.
	 *
	 * @param menu the machine menu
	 */
	public MachineRecipeBookComponent(MachineMenu menu) {
		super(menu, List.of(new RecipeBookComponent.TabInfo(menu.kind().machineItem(), menu.kind().category())));
	}

	@Override
	protected WidgetSprites getFilterButtonTextures() {
		return FILTER_SPRITES;
	}

	@Override
	protected boolean isCraftingSlot(Slot slot) {
		return slot.index <= MachineBlockEntity.ENERGY;
	}

	@Override
	protected void fillGhostRecipe(GhostSlots ghostSlots, RecipeDisplay recipe, ContextMap context) {
		ghostSlots.setResult(menu.getSlot(MachineBlockEntity.OUTPUT), context, recipe.result());
		if (recipe instanceof MachineRecipeDisplay display) {
			for (int i = 0; i < display.inputs().size(); i++) {
				ghostSlots.setInput(menu.getSlot(MachineBlockEntity.INPUT_0 + i), context, display.inputs().get(i));
			}
			Slot energy = menu.getSlot(MachineBlockEntity.ENERGY);
			if (energy.getItem().isEmpty()) {
				ghostSlots.setInput(energy, context, display.energy());
			}
		}
	}

	@Override
	protected Component getRecipeFilterName() {
		return FILTER_NAME;
	}

	@Override
	protected void selectMatchingRecipes(RecipeCollection collection, StackedItemContents stackedContents) {
		collection.selectRecipes(stackedContents, display -> display instanceof MachineRecipeDisplay);
	}
}
