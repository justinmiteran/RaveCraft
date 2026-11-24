package com.ravecraft.mod.compat.rei;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.recipe.CountedIngredient;
import com.ravecraft.mod.recipe.MachineKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.InputIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * The REI page of one machine recipe, built on the server and synced by REI.
 *
 * @param data the recipe and its id
 */
public record MachineDisplay(MachineDisplayData data) implements Display {
	/** The formats of {@link MachineDisplayData}. */
	public static final DisplaySerializer<MachineDisplay> SERIALIZER = DisplaySerializer.of(
			MachineDisplayData.MAP_CODEC.xmap(MachineDisplay::new, MachineDisplay::data),
			MachineDisplayData.STREAM_CODEC.map(MachineDisplay::new, MachineDisplay::data));

	/**
	 * Returns the REI category of a machine.
	 *
	 * @param kind the machine kind
	 * @return the category, named after the recipe type
	 */
	public static CategoryIdentifier<MachineDisplay> category(MachineKind kind) {
		return CategoryIdentifier.of(RaveCraft.id(kind.process()));
	}

	/** Every item an ingredient accepts, each at the given amount. */
	private static EntryIngredient stacks(Ingredient ingredient, int count) {
		return EntryIngredients.ofItemStacks(ingredient.items().map(item -> new ItemStack(item, count)).toList());
	}

	/** Each counted ingredient, then the mold. */
	@Override
	public List<EntryIngredient> getInputEntries() {
		List<EntryIngredient> entries = new ArrayList<>();
		for (CountedIngredient counted : data.recipe().ingredients()) {
			entries.add(stacks(counted.ingredient(), counted.count()));
		}
		data.recipe().mold().ifPresent(mold -> entries.add(stacks(mold, 1)));
		return entries;
	}

	/**
	 * One ingredient per item the machine takes, because REI moves one item per ingredient. Each is shown at the page
	 * slot of its counted ingredient, which REI marks when the item is missing.
	 */
	@Override
	public List<InputIngredient<EntryStack<?>>> getInputIngredients(AbstractContainerMenu menu, Player player) {
		List<Ingredient> units = data.units();
		List<Integer> slots = data.unitSlots();
		List<Integer> pageSlots = slots.stream().distinct().toList();
		List<InputIngredient<EntryStack<?>>> ingredients = new ArrayList<>();
		for (int i = 0; i < units.size(); i++) {
			ingredients.add(InputIngredient.of(i, pageSlots.indexOf(slots.get(i)), stacks(units.get(i), 1)));
		}
		return ingredients;
	}

	@Override
	public List<EntryIngredient> getOutputEntries() {
		return List.of(EntryIngredients.ofItemStacks(List.of(data.recipe().result().create())));
	}

	@Override
	public CategoryIdentifier<?> getCategoryIdentifier() {
		return category(data.recipe().kind());
	}

	@Override
	public Optional<Identifier> getDisplayLocation() {
		return Optional.of(data.id());
	}

	@Override
	public DisplaySerializer<? extends Display> getSerializer() {
		return SERIALIZER;
	}
}
