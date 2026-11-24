package com.ravecraft.mod.compat.rei;

import com.ravecraft.mod.blockentity.MachineBlockEntity;
import com.ravecraft.mod.recipe.CountedIngredient;
import com.ravecraft.mod.recipe.MachineRecipe;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * What the "+" button of REI asks for one operation. REI empties the given slots, then puts one item of unit <i>i</i>
 * in slot <i>i</i>, and destroys an item its slot refuses; so the choice of items is made here. Uses no REI class.
 *
 * @param units one entry per item to place
 * @param slots the menu slot of each unit, then the inputs the recipe leaves empty, which REI only empties
 */
public record MachineFill(List<Unit> units, List<Integer> slots) {
	/**
	 * One item to place.
	 *
	 * @param slot the menu slot
	 * @param pageSlot the slot of the recipe page to mark when the item is missing
	 * @param choices the accepted items, one of each
	 */
	public record Unit(int slot, int pageSlot, List<ItemStack> choices) {
	}

	/**
	 * Chooses the items of one operation: a single item per ingredient and never the same for two ingredients, because
	 * an input holds one kind of item and no two inputs hold the same. An accepted mold already in the machine stays.
	 *
	 * @param recipe the recipe
	 * @param mold what the mold slot holds
	 * @param inventory what the player holds
	 * @return the items to place and the slots
	 */
	public static MachineFill of(MachineRecipe recipe, ItemStack mold, List<ItemStack> inventory) {
		List<Unit> units = new ArrayList<>();
		Set<Item> taken = new HashSet<>();
		List<CountedIngredient> ingredients = recipe.ingredients();
		for (int i = 0; i < ingredients.size(); i++) {
			CountedIngredient counted = ingredients.get(i);
			Optional<Item> choice = choose(counted, inventory, taken);
			choice.ifPresent(taken::add);
			List<ItemStack> choices = choice.map(ItemStack::new).stream().toList();
			units.addAll(Collections.nCopies(counted.count(), new Unit(MachineBlockEntity.INPUT_0 + i, i, choices)));
		}
		recipe.mold().filter(accepted -> !accepted.test(mold)).ifPresent(accepted -> units.add(
				new Unit(MachineBlockEntity.MOLD, ingredients.size(), items(accepted).stream().map(ItemStack::new).toList())));
		List<Integer> slots = new ArrayList<>(units.stream().map(Unit::slot).toList());
		for (int i = ingredients.size(); i < recipe.kind().inputs(); i++) {
			slots.add(MachineBlockEntity.INPUT_0 + i);
		}
		return new MachineFill(units, slots);
	}

	/** The first item the player holds enough of, else the one they hold most of; empty when all are taken. */
	private static Optional<Item> choose(CountedIngredient counted, List<ItemStack> inventory, Set<Item> taken) {
		return items(counted.ingredient()).stream()
				.filter(item -> !taken.contains(item))
				.sorted(Comparator.comparingInt(item -> -Math.min(held(item, inventory), counted.count())))
				.findFirst();
	}

	/** How many of an item REI could take: the plain item only. */
	private static int held(Item item, List<ItemStack> inventory) {
		ItemStack plain = new ItemStack(item);
		return inventory.stream().filter(stack -> ItemStack.isSameItemSameComponents(stack, plain))
				.mapToInt(ItemStack::getCount).sum();
	}

	private static List<Item> items(Ingredient ingredient) {
		return ingredient.items().map(Holder::value).toList();
	}
}
