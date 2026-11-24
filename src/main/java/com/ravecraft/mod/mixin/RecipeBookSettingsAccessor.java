package com.ravecraft.mod.mixin;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.stats.RecipeBookSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Replaces the settings codecs with ones that also carry the machine book. */
@Mixin(RecipeBookSettings.class)
public interface RecipeBookSettingsAccessor {
	@Mutable
	@Accessor("STREAM_CODEC")
	static void ravecraft$setStreamCodec(StreamCodec<FriendlyByteBuf, RecipeBookSettings> codec) {
		throw new AssertionError();
	}

	@Mutable
	@Accessor("MAP_CODEC")
	static void ravecraft$setMapCodec(MapCodec<RecipeBookSettings> codec) {
		throw new AssertionError();
	}
}
