package com.ravecraft.mod.compat.rei;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.ravecraft.mod.blockentity.MachineBlockEntity;
import com.ravecraft.mod.recipe.CountedIngredient;
import com.ravecraft.mod.recipe.MachineRecipe;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * What REI sends to the client for one machine recipe: the recipe itself, in its own formats, and its id. Uses no REI
 * class, so it loads (and is tested) without REI.
 *
 * @param id the recipe id
 * @param recipe the recipe
 */
public record MachineDisplayData(Identifier id, MachineRecipe recipe) {
	private static final Codec<MachineRecipe> RECIPE_CODEC = Recipe.DIRECT_CODEC.comapFlatMap(
			recipe -> recipe instanceof MachineRecipe machine ? DataResult.success(machine)
					: DataResult.error(() -> "Not a machine recipe: " + recipe.getType()),
			recipe -> recipe);
	private static final StreamCodec<RegistryFriendlyByteBuf, MachineRecipe> RECIPE_STREAM_CODEC =
			Recipe.STREAM_CODEC.map(MachineDisplayData::machineRecipe, recipe -> recipe);
	/** Saved form. */
	public static final MapCodec<MachineDisplayData> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Identifier.CODEC.fieldOf("id").forGetter(MachineDisplayData::id),
			RECIPE_CODEC.fieldOf("recipe").forGetter(MachineDisplayData::recipe))
			.apply(i, MachineDisplayData::new));
	/** Network form. */
	public static final StreamCodec<RegistryFriendlyByteBuf, MachineDisplayData> STREAM_CODEC = StreamCodec.composite(
			Identifier.STREAM_CODEC, MachineDisplayData::id,
			RECIPE_STREAM_CODEC, MachineDisplayData::recipe,
			MachineDisplayData::new);

	/**
	 * Creates the data of a loaded recipe.
	 *
	 * @param holder the recipe and its id
	 * @return the data
	 */
	public static MachineDisplayData of(RecipeHolder<MachineRecipe> holder) {
		return new MachineDisplayData(holder.id().identifier(), holder.value());
	}

	private static MachineRecipe machineRecipe(Recipe<?> recipe) {
		if (recipe instanceof MachineRecipe machine) {
			return machine;
		}
		throw new DecoderException("Not a machine recipe: " + recipe.getType());
	}

	/**
	 * Returns the machine slot of each item one operation takes: each ingredient's slot repeated by its count, then the
	 * mold slot.
	 *
	 * @return the menu slot indices, matching {@link #units()}
	 */
	public List<Integer> unitSlots() {
		List<Integer> slots = new ArrayList<>();
		List<CountedIngredient> ingredients = recipe.ingredients();
		for (int i = 0; i < ingredients.size(); i++) {
			slots.addAll(Collections.nCopies(ingredients.get(i).count(), MachineBlockEntity.INPUT_0 + i));
		}
		recipe.mold().ifPresent(mold -> slots.add(MachineBlockEntity.MOLD));
		return slots;
	}

	/**
	 * Returns the ingredient of each item one operation takes: each ingredient repeated by its count, then the mold.
	 *
	 * @return the ingredients, matching {@link #unitSlots()}
	 */
	public List<Ingredient> units() {
		List<Ingredient> units = new ArrayList<>();
		for (CountedIngredient counted : recipe.ingredients()) {
			units.addAll(Collections.nCopies(counted.count(), counted.ingredient()));
		}
		recipe.mold().ifPresent(units::add);
		return units;
	}
}
