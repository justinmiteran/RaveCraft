package com.ravecraft.mod.gametest;

import com.ravecraft.mod.registry.ModBlocks;
import com.ravecraft.mod.registry.ModCreativeTabs;
import com.ravecraft.mod.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;

public class MaterialsGameTest {
	@GameTest
	public void oresRequireTheirTool(GameTestHelper h) {
		for (Block ore : ModBlocks.ores()) {
			h.assertTrue(ore instanceof DropExperienceBlock, ore + " drops experience");
			h.assertTrue(ore.defaultBlockState().requiresCorrectToolForDrops(), ore + " needs a tool");
		}
		h.succeed();
	}

	@GameTest
	public void shaleFamilyShapes(GameTestHelper h) {
		for (Block stairs : List.of(ModBlocks.SHALE_STAIRS, ModBlocks.POLISHED_SHALE_STAIRS, ModBlocks.SHALE_BRICK_STAIRS)) {
			h.assertTrue(stairs instanceof StairBlock, stairs + " is a stair");
		}
		for (Block slab : List.of(ModBlocks.SHALE_SLAB, ModBlocks.POLISHED_SHALE_SLAB, ModBlocks.SHALE_BRICK_SLAB)) {
			h.assertTrue(slab instanceof SlabBlock, slab + " is a slab");
		}
		for (Block wall : List.of(ModBlocks.SHALE_WALL, ModBlocks.POLISHED_SHALE_WALL, ModBlocks.SHALE_BRICK_WALL)) {
			h.assertTrue(wall instanceof WallBlock, wall + " is a wall");
		}
		for (Block block : ModBlocks.shaleFamily()) {
			h.assertTrue(block.defaultBlockState().requiresCorrectToolForDrops(), block + " needs a pickaxe");
		}
		h.assertValueEqual(ModBlocks.shaleFamily().size(), 13, "shale family size");
		h.succeed();
	}

	@GameTest
	public void materialsInCreativeTab(GameTestHelper h) {
		CreativeModeTabs.tryRebuildTabContents(h.getLevel().enabledFeatures(), false, h.getLevel().registryAccess());
		List<ItemLike> expected = new ArrayList<>(ModBlocks.ores());
		expected.addAll(ModBlocks.shaleFamily());
		expected.addAll(ModItems.materials());
		for (ItemLike item : expected) {
			h.assertTrue(ModCreativeTabs.RAVECRAFT.contains(new ItemStack(item)), item + " in the RaveCraft tab");
		}
		h.succeed();
	}
}
