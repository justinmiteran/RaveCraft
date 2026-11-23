package com.ravecraft.mod.recipe;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

/**
 * The contents a machine recipe is matched against: the input slots, then the mold slot.
 *
 * @param inputs the input slots, in order
 * @param mold the mold slot, empty for machines without one
 */
public record MachineRecipeInput(List<ItemStack> inputs, ItemStack mold) implements RecipeInput {
	@Override
	public ItemStack getItem(int index) {
		return index < inputs.size() ? inputs.get(index) : mold;
	}

	@Override
	public int size() {
		return inputs.size() + 1;
	}
}
