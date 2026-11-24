package com.ravecraft.mod.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.ravecraft.mod.core.machine.MachineConstants;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;

/**
 * What the recipe book shows of a machine recipe: one entry per ingredient with its count, the mold, the energy items
 * (as a ghost, like the furnace fuel), the result and the machine.
 *
 * @param inputs the ingredients, one per input slot
 * @param counts the count of each ingredient
 * @param mold the mold, or {@link SlotDisplay.Empty}
 * @param energy the accepted energy items
 * @param result the result
 * @param craftingStation the machine
 * @param duration the operation time, in ticks
 */
public record MachineRecipeDisplay(List<SlotDisplay> inputs, List<Integer> counts, SlotDisplay mold,
		SlotDisplay energy, SlotDisplay result, SlotDisplay craftingStation, int duration) implements RecipeDisplay {
	/** JSON form, required by the display registry. */
	public static final MapCodec<MachineRecipeDisplay> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			SlotDisplay.CODEC.listOf().fieldOf("inputs").forGetter(MachineRecipeDisplay::inputs),
			Codec.INT.listOf().fieldOf("counts").forGetter(MachineRecipeDisplay::counts),
			SlotDisplay.CODEC.fieldOf("mold").forGetter(MachineRecipeDisplay::mold),
			SlotDisplay.CODEC.fieldOf("energy").forGetter(MachineRecipeDisplay::energy),
			SlotDisplay.CODEC.fieldOf("result").forGetter(MachineRecipeDisplay::result),
			SlotDisplay.CODEC.fieldOf("crafting_station").forGetter(MachineRecipeDisplay::craftingStation),
			Codec.INT.fieldOf("duration").forGetter(MachineRecipeDisplay::duration))
			.apply(i, MachineRecipeDisplay::new));
	/** Network form, sent with the recipe book. */
	public static final StreamCodec<RegistryFriendlyByteBuf, MachineRecipeDisplay> STREAM_CODEC =
			StreamCodec.composite(
					SlotDisplay.STREAM_CODEC.apply(ByteBufCodecs.list(MachineConstants.MAX_INGREDIENTS)),
					MachineRecipeDisplay::inputs,
					ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MachineConstants.MAX_INGREDIENTS)),
					MachineRecipeDisplay::counts,
					SlotDisplay.STREAM_CODEC, MachineRecipeDisplay::mold,
					SlotDisplay.STREAM_CODEC, MachineRecipeDisplay::energy,
					SlotDisplay.STREAM_CODEC, MachineRecipeDisplay::result,
					SlotDisplay.STREAM_CODEC, MachineRecipeDisplay::craftingStation,
					ByteBufCodecs.VAR_INT, MachineRecipeDisplay::duration,
					MachineRecipeDisplay::new);
	/** The display type, registered as {@code ravecraft:machine}. */
	public static final RecipeDisplay.Type<MachineRecipeDisplay> TYPE =
			new RecipeDisplay.Type<>(MAP_CODEC, STREAM_CODEC);

	/**
	 * The energy items as a slot display: redstone dust and redstone block.
	 *
	 * @return a display cycling through both
	 */
	public static SlotDisplay energyItems() {
		return new SlotDisplay.Composite(List.of(new SlotDisplay.ItemSlotDisplay(Items.REDSTONE),
				new SlotDisplay.ItemSlotDisplay(Items.REDSTONE_BLOCK)));
	}

	@Override
	public RecipeDisplay.Type<MachineRecipeDisplay> type() {
		return TYPE;
	}

	@Override
	public boolean isEnabled(FeatureFlagSet enabledFeatures) {
		return inputs.stream().allMatch(input -> input.isEnabled(enabledFeatures)) && mold.isEnabled(enabledFeatures)
				&& RecipeDisplay.super.isEnabled(enabledFeatures);
	}
}
