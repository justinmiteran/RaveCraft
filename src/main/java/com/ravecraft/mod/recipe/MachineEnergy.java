package com.ravecraft.mod.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.ravecraft.mod.core.machine.MachineConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/**
 * An item that powers the machines, and how many operations one item gives. Data-driven like the furnace fuels of
 * tech mods: one {@code ravecraft:machine_energy} recipe per item or tag, so a datapack can add, change or remove
 * energy sources. The recipes are synchronised, so the client (energy slot, recipe viewers) reads the same values.
 *
 * @param ingredient the items this entry accepts
 * @param operations the operations one item gives, 1 to {@link MachineConstants#MAX_ENERGY}
 */
public record MachineEnergy(Ingredient ingredient, int operations) implements Recipe<SingleRecipeInput> {
	private static final MapCodec<MachineEnergy> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Ingredient.CODEC.fieldOf("ingredient").forGetter(MachineEnergy::ingredient),
			Codec.intRange(1, MachineConstants.MAX_ENERGY).fieldOf("operations").forGetter(MachineEnergy::operations))
			.apply(i, MachineEnergy::new));
	private static final StreamCodec<RegistryFriendlyByteBuf, MachineEnergy> STREAM_CODEC = StreamCodec.composite(
			Ingredient.CONTENTS_STREAM_CODEC, MachineEnergy::ingredient,
			ByteBufCodecs.VAR_INT, MachineEnergy::operations,
			MachineEnergy::new);
	/** The recipe type, {@code ravecraft:machine_energy}. */
	public static final RecipeType<MachineEnergy> TYPE = new RecipeType<>() {
		@Override
		public String toString() {
			return "machine_energy";
		}
	};
	/** The serializer, registered and synchronised by {@code ModRecipes}. */
	public static final RecipeSerializer<MachineEnergy> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

	/**
	 * The operations one item of a stack gives, on either side.
	 *
	 * @param level the level whose recipes are read
	 * @param stack the stack
	 * @return the operations of the first entry accepting the item, or 0 when the item does not power machines
	 */
	public static int operations(Level level, ItemStack stack) {
		if (stack.isEmpty()) {
			return 0;
		}
		return level.recipeAccess().getSynchronizedRecipes().getAllOfType(TYPE).stream()
				.filter(holder -> holder.value().ingredient().test(stack))
				.mapToInt(holder -> holder.value().operations())
				.findFirst().orElse(0);
	}

	@Override
	public boolean matches(SingleRecipeInput input, Level level) {
		return ingredient.test(input.item());
	}

	@Override
	public ItemStack assemble(SingleRecipeInput input) {
		return ItemStack.EMPTY;
	}

	/** Not a crafting recipe: no recipe book entry, never unlocked. */
	@Override
	public boolean isSpecial() {
		return true;
	}

	@Override
	public boolean showNotification() {
		return false;
	}

	@Override
	public String group() {
		return "";
	}

	@Override
	public RecipeSerializer<MachineEnergy> getSerializer() {
		return SERIALIZER;
	}

	@Override
	public RecipeType<MachineEnergy> getType() {
		return TYPE;
	}

	@Override
	public PlacementInfo placementInfo() {
		return PlacementInfo.NOT_PLACEABLE;
	}

	@Override
	public RecipeBookCategory recipeBookCategory() {
		return RecipeBookCategories.CRAFTING_MISC;
	}
}
