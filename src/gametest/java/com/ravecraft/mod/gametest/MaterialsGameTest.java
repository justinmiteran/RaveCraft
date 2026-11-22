package com.ravecraft.mod.gametest;

import com.ravecraft.mod.registry.ModBlocks;
import com.ravecraft.mod.registry.ModCreativeTabs;
import com.ravecraft.mod.registry.ModItems;
import com.ravecraft.mod.registry.ModTags;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

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

	@GameTest
	public void oilShaleDropsBitumen(GameTestHelper h) {
		for (int i = 0; i < 20; i++) {
			List<ItemStack> drops = drops(h, ModBlocks.OIL_SHALE.defaultBlockState(), new ItemStack(Items.STONE_PICKAXE));
			h.assertValueEqual(drops.size(), 1, "one stack");
			h.assertTrue(drops.get(0).is(ModItems.BITUMEN), "bitumen, got " + drops);
			int count = drops.get(0).getCount();
			h.assertTrue(count >= 1 && count <= 3, "1-3 bitumen, got " + count);
		}
		h.succeed();
	}

	@GameTest
	public void neodymiumDropsRaw(GameTestHelper h) {
		List<ItemStack> drops = drops(h, ModBlocks.DEEPSLATE_NEODYMIUM_ORE.defaultBlockState(),
				new ItemStack(Items.IRON_PICKAXE));
		h.assertValueEqual(drops.size(), 1, "one stack");
		h.assertTrue(drops.get(0).is(ModItems.RAW_NEODYMIUM), "raw neodymium, got " + drops);
		h.assertValueEqual(drops.get(0).getCount(), 1, "one raw neodymium");
		h.succeed();
	}

	@GameTest
	public void silkTouchDropsOre(GameTestHelper h) {
		ItemStack tool = enchanted(h, Enchantments.SILK_TOUCH, 1);
		tool.enchant(enchantment(h, Enchantments.FORTUNE), 3);
		for (Block ore : ModBlocks.ores()) {
			List<ItemStack> drops = drops(h, ore.defaultBlockState(), tool);
			h.assertValueEqual(drops.size(), 1, ore + " one stack");
			h.assertTrue(drops.get(0).is(ore.asItem()), ore + " drops itself, got " + drops);
			h.assertValueEqual(drops.get(0).getCount(), 1, ore + " count");
		}
		h.succeed();
	}

	@GameTest
	public void fortuneRaisesDrops(GameTestHelper h) {
		ItemStack tool = enchanted(h, Enchantments.FORTUNE, 3);
		int max = 0;
		for (int i = 0; i < 50; i++) {
			for (ItemStack stack : drops(h, ModBlocks.OIL_SHALE.defaultBlockState(), tool)) {
				max = Math.max(max, stack.getCount());
			}
		}
		h.assertTrue(max > 3, "fortune drops more than 3, max " + max);
		h.succeed();
	}

	@GameTest
	public void toolTiers(GameTestHelper h) {
		BlockState oil = ModBlocks.OIL_SHALE.defaultBlockState();
		BlockState neodymium = ModBlocks.DEEPSLATE_NEODYMIUM_ORE.defaultBlockState();
		h.assertFalse(new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(oil), "wood on oil shale");
		h.assertTrue(new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(oil), "stone on oil shale");
		h.assertFalse(new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(neodymium), "stone on neodymium");
		h.assertTrue(new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(neodymium), "iron on neodymium");
		for (Block block : ModBlocks.shaleFamily()) {
			h.assertTrue(new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(block.defaultBlockState()),
					"wood on " + block);
		}
		h.succeed();
	}

	@GameTest
	public void shaleBlocksDropThemselves(GameTestHelper h) {
		ItemStack tool = new ItemStack(Items.WOODEN_PICKAXE);
		for (Block block : ModBlocks.shaleFamily()) {
			List<ItemStack> drops = drops(h, block.defaultBlockState(), tool);
			h.assertValueEqual(drops.size(), 1, block + " one stack");
			h.assertTrue(drops.get(0).is(block.asItem()), block + " drops itself, got " + drops);
			h.assertValueEqual(drops.get(0).getCount(), 1, block + " count");
		}
		BlockState doubleSlab = ModBlocks.SHALE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE);
		h.assertValueEqual(count(drops(h, doubleSlab, tool), ModBlocks.SHALE_SLAB.asItem()), 2, "double slab drops 2");
		h.succeed();
	}

	@GameTest
	public void commonTags(GameTestHelper h) {
		for (Block ore : ModBlocks.ores()) {
			h.assertTrue(ore.defaultBlockState().is(ModTags.Blocks.ORES), ore + " in c:ores");
			h.assertTrue(new ItemStack(ore).is(ModTags.Items.ORES), ore + " item in c:ores");
		}
		h.assertTrue(ModBlocks.OIL_SHALE.defaultBlockState().is(ModTags.Blocks.ORES_IN_GROUND_STONE), "oil in stone");
		h.assertTrue(ModBlocks.DEEPSLATE_NEODYMIUM_ORE.defaultBlockState().is(ModTags.Blocks.ORES_IN_GROUND_DEEPSLATE),
				"neodymium in deepslate");
		h.assertTrue(new ItemStack(ModItems.RAW_NEODYMIUM).is(ModTags.Items.RAW_MATERIALS_NEODYMIUM), "raw tag");
		h.assertTrue(new ItemStack(ModItems.NEODYMIUM_INGOT).is(ModTags.Items.INGOTS_NEODYMIUM), "ingot tag");
		h.assertTrue(new ItemStack(ModItems.SILICON).is(ModTags.Items.SILICON), "silicon tag");
		for (Block stairs : List.of(ModBlocks.SHALE_STAIRS, ModBlocks.POLISHED_SHALE_STAIRS, ModBlocks.SHALE_BRICK_STAIRS)) {
			h.assertTrue(stairs.defaultBlockState().is(BlockTags.STAIRS), stairs + " in stairs");
		}
		for (Block slab : List.of(ModBlocks.SHALE_SLAB, ModBlocks.POLISHED_SHALE_SLAB, ModBlocks.SHALE_BRICK_SLAB)) {
			h.assertTrue(slab.defaultBlockState().is(BlockTags.SLABS), slab + " in slabs");
		}
		for (Block wall : List.of(ModBlocks.SHALE_WALL, ModBlocks.POLISHED_SHALE_WALL, ModBlocks.SHALE_BRICK_WALL)) {
			h.assertTrue(wall.defaultBlockState().is(BlockTags.WALLS), wall + " in walls");
		}
		h.succeed();
	}

	@GameTest
	public void explosionsDecayDrops(GameTestHelper h) {
		BlockState oil = ModBlocks.OIL_SHALE.defaultBlockState();
		ItemStack tool = new ItemStack(Items.STONE_PICKAXE);
		int exploded = 0;
		int mined = 0;
		for (int i = 0; i < 50; i++) {
			exploded += count(oil.getDrops(params(h, tool).withParameter(LootContextParams.EXPLOSION_RADIUS, 100.0F)),
					ModItems.BITUMEN);
			mined += count(drops(h, oil, tool), ModItems.BITUMEN);
		}
		h.assertTrue(exploded < 25, "explosion decays drops, got " + exploded);
		h.assertTrue(mined >= 50, "mining drops at least 50, got " + mined);
		h.succeed();
	}

	private static List<ItemStack> drops(GameTestHelper h, BlockState state, ItemStack tool) {
		return Block.getDrops(state, h.getLevel(), h.absolutePos(BlockPos.ZERO), null, null, tool);
	}

	private static LootParams.Builder params(GameTestHelper h, ItemStack tool) {
		return new LootParams.Builder(h.getLevel())
				.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(h.absolutePos(BlockPos.ZERO)))
				.withParameter(LootContextParams.TOOL, tool);
	}

	private static int count(List<ItemStack> drops, Item item) {
		return drops.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
	}

	private static Holder<Enchantment> enchantment(GameTestHelper h, ResourceKey<Enchantment> key) {
		return h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	private static ItemStack enchanted(GameTestHelper h, ResourceKey<Enchantment> key, int level) {
		ItemStack tool = new ItemStack(Items.IRON_PICKAXE);
		tool.enchant(enchantment(h, key), level);
		return tool;
	}
}
