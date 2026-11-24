package com.ravecraft.mod.recipe;

import com.ravecraft.mod.RaveCraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * One kind of machine and its recipes: the recipe type and serializer (both named after the process, e.g.
 * {@code winding}), the recipe book tab (named after the machine), how many input slots the machine uses and
 * whether recipes need a mold. Built here,
 * registered by {@code ModRecipes}.
 */
public final class MachineKind {
	private final String process;
	private final String machine;
	private final int inputs;
	private final boolean needsMold;
	private final RecipeType<MachineRecipe> type;
	private final RecipeSerializer<MachineRecipe> serializer;
	private final RecipeBookCategory category = new RecipeBookCategory();

	/**
	 * Creates a kind; nothing is registered yet.
	 *
	 * @param process the process name, id of the recipe type and serializer (e.g. {@code winding})
	 * @param machine the machine's block id path, id of the recipe book category (e.g. {@code coil_winder})
	 * @param inputs how many input slots the machine uses, 1 to 3; recipes take at most that many ingredients
	 * @param needsMold whether every recipe of this kind names a mold
	 */
	public MachineKind(String process, String machine, int inputs, boolean needsMold) {
		this.process = process;
		this.machine = machine;
		this.inputs = inputs;
		this.needsMold = needsMold;
		this.type = new RecipeType<>() {
			@Override
			public String toString() {
				return process;
			}
		};
		this.serializer = new RecipeSerializer<>(MachineRecipe.mapCodec(this), MachineRecipe.streamCodec(this));
	}

	/**
	 * Returns the process name.
	 *
	 * @return the id path of the recipe type and serializer
	 */
	public String process() {
		return process;
	}

	/**
	 * Returns the machine's id path.
	 *
	 * @return the id path of the machine block and of its recipe book category
	 */
	public String machine() {
		return machine;
	}

	/**
	 * Returns how many input slots the machine uses.
	 *
	 * @return 1 to 3; the other input slots stay empty and hidden
	 */
	public int inputs() {
		return inputs;
	}

	/**
	 * Returns whether recipes need a mold.
	 *
	 * @return true when a recipe without a mold is invalid (and false when a mold is not allowed)
	 */
	public boolean needsMold() {
		return needsMold;
	}

	/**
	 * Returns the recipe type.
	 *
	 * @return the recipe type of this kind
	 */
	public RecipeType<MachineRecipe> type() {
		return type;
	}

	/**
	 * Returns the serializer.
	 *
	 * @return the serializer building recipes of this kind
	 */
	public RecipeSerializer<MachineRecipe> serializer() {
		return serializer;
	}

	/**
	 * Returns the recipe book category.
	 *
	 * @return the recipe book tab of the machine
	 */
	public RecipeBookCategory category() {
		return category;
	}

	/**
	 * Returns the machine's item, once the block is registered.
	 *
	 * @return the machine block item, or air before registration
	 */
	public Item machineItem() {
		return BuiltInRegistries.ITEM.getValue(RaveCraft.id(machine));
	}

	@Override
	public String toString() {
		return process;
	}
}
