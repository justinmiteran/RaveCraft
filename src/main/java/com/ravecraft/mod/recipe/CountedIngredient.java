package com.ravecraft.mod.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * A machine recipe ingredient and how many of it one operation takes.
 *
 * @param ingredient the accepted items
 * @param count the items consumed per operation, at least 1
 */
public record CountedIngredient(Ingredient ingredient, int count) {
	/** JSON form: {@code {"ingredient": ..., "count": n}}, count defaulting to 1. */
	public static final Codec<CountedIngredient> CODEC = RecordCodecBuilder.create(i -> i.group(
			Ingredient.CODEC.fieldOf("ingredient").forGetter(CountedIngredient::ingredient),
			ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(CountedIngredient::count))
			.apply(i, CountedIngredient::new));
	/** Network form. */
	public static final StreamCodec<RegistryFriendlyByteBuf, CountedIngredient> STREAM_CODEC = StreamCodec.composite(
			Ingredient.CONTENTS_STREAM_CODEC, CountedIngredient::ingredient,
			ByteBufCodecs.VAR_INT, CountedIngredient::count,
			CountedIngredient::new);
}
