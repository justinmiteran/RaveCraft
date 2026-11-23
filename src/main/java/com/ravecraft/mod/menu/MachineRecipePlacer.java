package com.ravecraft.mod.menu;

import com.ravecraft.mod.recipe.CountedIngredient;
import com.ravecraft.mod.recipe.MachineRecipe;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.RecipeBookMenu.PostPlaceAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Places a machine recipe clicked in the recipe book: ingredient {@code i} goes to input slot {@code i}, with its
 * count, once or as many times as the stacks and the inventory allow. When an ingredient is missing nothing moves
 * and the book shows the ghost recipe; the inputs already there go back to the player first, and nothing happens if
 * they would not fit.
 */
public final class MachineRecipePlacer {
	private MachineRecipePlacer() {
	}

	/**
	 * Places a recipe in a machine menu.
	 *
	 * @param menu the machine menu
	 * @param recipe the recipe clicked
	 * @param useMaxItems whether to place as many crafts as possible (shift-click)
	 * @param inventory the player's inventory
	 * @return {@code PLACE_GHOST_RECIPE} when an ingredient is missing, {@code NOTHING} otherwise
	 */
	public static PostPlaceAction place(MachineMenu menu, MachineRecipe recipe, boolean useMaxItems,
			Inventory inventory) {
		List<Slot> inputs = menu.inputSlots();
		List<ItemStack> sources = new ArrayList<>(inventory.getNonEquipmentItems());
		inputs.forEach(slot -> sources.add(slot.getItem()));
		List<ItemStack> chosen = recipe.ingredients().stream().map(ingredient -> pick(ingredient, sources)).toList();
		int crafts = crafts(recipe, chosen, sources, useMaxItems);
		if (crafts == 0) {
			return PostPlaceAction.PLACE_GHOST_RECIPE;
		}
		if (!returnInputs(inputs, inventory)) {
			return PostPlaceAction.NOTHING;
		}
		for (int i = 0; i < chosen.size(); i++) {
			int amount = recipe.ingredients().get(i).count() * crafts;
			inputs.get(i).set(take(inventory, chosen.get(i), amount));
		}
		return PostPlaceAction.NOTHING;
	}

	/** The matching item the player has the most of, as a one-item template, or empty when none matches. */
	private static ItemStack pick(CountedIngredient ingredient, List<ItemStack> sources) {
		ItemStack best = ItemStack.EMPTY;
		int bestTotal = 0;
		for (ItemStack stack : sources) {
			int total = ingredient.ingredient().test(stack) ? total(sources, stack) : 0;
			if (total > bestTotal) {
				best = stack.copyWithCount(1);
				bestTotal = total;
			}
		}
		return best;
	}

	private static int total(List<ItemStack> sources, ItemStack template) {
		return sources.stream().filter(stack -> ItemStack.isSameItemSameComponents(stack, template))
				.mapToInt(ItemStack::getCount).sum();
	}

	/** How many crafts to place: 1, or the most the stacks hold; 0 when the player has not enough for one. */
	private static int crafts(MachineRecipe recipe, List<ItemStack> chosen, List<ItemStack> sources,
			boolean useMaxItems) {
		int crafts = Integer.MAX_VALUE;
		for (int i = 0; i < chosen.size(); i++) {
			ItemStack item = chosen.get(i);
			int count = recipe.ingredients().get(i).count();
			int limit = useMaxItems ? item.getMaxStackSize() / count : 1;
			crafts = item.isEmpty() ? 0 : Math.min(crafts, Math.min(limit, total(sources, item) / count));
		}
		return crafts;
	}

	/** Moves the inputs back to the inventory, if they all fit. */
	private static boolean returnInputs(List<Slot> inputs, Inventory inventory) {
		List<ItemStack> simulated = new ArrayList<>();
		inventory.getNonEquipmentItems().forEach(stack -> simulated.add(stack.copy()));
		for (Slot slot : inputs) {
			if (!fits(simulated, slot.getItem().copy())) {
				return false;
			}
		}
		for (Slot slot : inputs) {
			inventory.add(slot.getItem().copy());
			slot.set(ItemStack.EMPTY);
		}
		return true;
	}

	/** Adds a stack to a simulated inventory, merging first, then into empty slots. */
	private static boolean fits(List<ItemStack> simulated, ItemStack stack) {
		for (ItemStack here : simulated) {
			if (ItemStack.isSameItemSameComponents(here, stack)) {
				int moved = Math.min(stack.getCount(), here.getMaxStackSize() - here.getCount());
				here.grow(moved);
				stack.shrink(moved);
			}
		}
		for (int i = 0; i < simulated.size() && !stack.isEmpty(); i++) {
			if (simulated.get(i).isEmpty()) {
				simulated.set(i, stack.split(stack.getMaxStackSize()));
			}
		}
		return stack.isEmpty();
	}

	/** Takes an amount of one item from the inventory. */
	private static ItemStack take(Inventory inventory, ItemStack template, int amount) {
		int left = amount;
		List<ItemStack> items = inventory.getNonEquipmentItems();
		for (int slot = 0; slot < items.size() && left > 0; slot++) {
			if (ItemStack.isSameItemSameComponents(items.get(slot), template)) {
				left -= inventory.removeItem(slot, left).getCount();
			}
		}
		return template.copyWithCount(amount - left);
	}
}
