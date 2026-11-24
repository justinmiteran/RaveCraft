package com.ravecraft.mod.client.compat;

import com.ravecraft.mod.recipe.MachineEnergy;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.item.ItemStack;

/** The machine energy items the recipe viewers show, read from the recipes the server synchronises. */
public final class MachineEnergyView {
	private MachineEnergyView() {
	}

	/**
	 * Returns every item of the synchronised {@link MachineEnergy} recipes, in recipe order.
	 *
	 * @return one stack per item, empty outside a world
	 */
	public static List<ItemStack> items() {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return List.of();
		}
		return level.recipeAccess().getSynchronizedRecipes().getAllOfType(MachineEnergy.TYPE).stream()
				.flatMap(holder -> holder.value().ingredient().items())
				.map(ItemStack::new).toList();
	}

	/**
	 * Returns how many operations an energy item gives.
	 *
	 * @param stack the energy item
	 * @return the operations, 0 outside a world
	 */
	public static int operations(ItemStack stack) {
		ClientLevel level = Minecraft.getInstance().level;
		return level == null ? 0 : MachineEnergy.operations(level, stack);
	}
}
