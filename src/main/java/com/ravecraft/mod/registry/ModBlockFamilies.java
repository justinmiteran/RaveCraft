package com.ravecraft.mod.registry;

import net.minecraft.data.BlockFamilies;
import net.minecraft.data.BlockFamily;

/**
 * The shale block families, built like vanilla tuff, so datagen derives models and recipes from them. Building a
 * family registers it with vanilla, which then chains stonecutter recipes from shale through every derived family.
 * Only datagen reads this class.
 */
public final class ModBlockFamilies {
	public static final BlockFamily SHALE = BlockFamilies.familyBuilder(ModBlocks.SHALE)
			.stairs(ModBlocks.SHALE_STAIRS)
			.slab(ModBlocks.SHALE_SLAB)
			.wall(ModBlocks.SHALE_WALL)
			.polished(ModBlocks.POLISHED_SHALE)
			.generateStonecutterRecipe()
			.getFamily();
	public static final BlockFamily POLISHED_SHALE = BlockFamilies.familyBuilder(ModBlocks.POLISHED_SHALE)
			.stairs(ModBlocks.POLISHED_SHALE_STAIRS)
			.slab(ModBlocks.POLISHED_SHALE_SLAB)
			.wall(ModBlocks.POLISHED_SHALE_WALL)
			.bricks(ModBlocks.SHALE_BRICKS)
			.generateStonecutterRecipe()
			.getFamily();
	public static final BlockFamily SHALE_BRICKS = BlockFamilies.familyBuilder(ModBlocks.SHALE_BRICKS)
			.stairs(ModBlocks.SHALE_BRICK_STAIRS)
			.slab(ModBlocks.SHALE_BRICK_SLAB)
			.wall(ModBlocks.SHALE_BRICK_WALL)
			.chiseled(ModBlocks.CHISELED_SHALE)
			.generateStonecutterRecipe()
			.getFamily();

	private ModBlockFamilies() {
	}
}
