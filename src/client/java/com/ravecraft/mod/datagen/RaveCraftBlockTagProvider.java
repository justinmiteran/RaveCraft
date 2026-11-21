package com.ravecraft.mod.datagen;

import com.ravecraft.mod.core.acoustics.occlusion.OcclusionSettings;
import com.ravecraft.mod.registry.ModBlocks;
import com.ravecraft.mod.world.acoustics.AcousticTags;
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
	/** A provider writing to {@code output}. */
	public RaveCraftBlockTagProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
		super(output, registries);
	}

	@Override
	protected void addTags(HolderLookup.Provider registries) {
		builder(BlockTags.MINEABLE_WITH_PICKAXE).add(key(ModBlocks.PORTABLE_SPEAKER));

		// Acoustic materials: looked at before the footstep sound, so datapacks can override them.
		for (String name : OcclusionSettings.BASE_MATERIALS) {
			builder(AcousticTags.material(name));
		}
		builder(AcousticTags.material("foliage")).addOptionalTag(BlockTags.LEAVES);
		builder(AcousticTags.material("wool")).addOptionalTag(BlockTags.WOOL).addOptionalTag(BlockTags.WOOL_CARPETS);
		builder(AcousticTags.material("wood")).addOptionalTag(BlockTags.PLANKS).addOptionalTag(BlockTags.LOGS);
	}

	private static ResourceKey<Block> key(Block block) {
		return BuiltInRegistries.BLOCK.getResourceKey(block).orElseThrow();
	}
}
