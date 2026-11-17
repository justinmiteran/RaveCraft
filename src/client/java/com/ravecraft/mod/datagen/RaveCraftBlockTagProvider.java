package com.ravecraft.mod.datagen;

import com.ravecraft.mod.registry.ModBlocks;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;

/** Blocks with {@code requiresCorrectToolForDrops} need a tool tag, or they drop nothing. */
public class RaveCraftBlockTagProvider extends FabricTagsProvider.BlockTagsProvider {
	public RaveCraftBlockTagProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
		super(output, registries);
	}

	@Override
	protected void addTags(HolderLookup.Provider registries) {
		builder(BlockTags.MINEABLE_WITH_PICKAXE).add(key(ModBlocks.PORTABLE_SPEAKER));
	}

	private static ResourceKey<Block> key(Block block) {
		return BuiltInRegistries.BLOCK.getResourceKey(block).orElseThrow();
	}
}
