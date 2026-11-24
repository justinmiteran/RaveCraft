package com.ravecraft.mod.gametest;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.registry.ModBlocks;
import com.ravecraft.mod.registry.ModCreativeTabs;
import com.ravecraft.mod.registry.ModItems;
import com.ravecraft.mod.registry.ModTags;
import com.ravecraft.mod.world.gen.ModWorldgen;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

public class MaterialsGameTest {
	private static final Cook SMELT = new Cook(RecipeType.SMELTING, "smelting", 200);
	private static final Cook BLAST = new Cook(RecipeType.BLASTING, "blasting", 100);

	/** A cooking station: its recipe type, the recipe id word and the expected cooking time. */
	private record Cook(RecipeType<?> type, String kind, int time) {
	}

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
		expected.addAll(ModBlocks.storageBlocks());
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
		for (Block ore : ModBlocks.ores()) {
			h.assertFalse(ItemStack.EMPTY.isCorrectToolForDrops(ore.defaultBlockState()), "bare hand on " + ore);
		}
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
			h.assertTrue(ore.defaultBlockState().is(ConventionalBlockTags.ORES), ore + " in c:ores");
			h.assertTrue(new ItemStack(ore).is(ConventionalItemTags.ORES), ore + " item in c:ores");
		}
		h.assertTrue(ModBlocks.OIL_SHALE.defaultBlockState().is(ConventionalBlockTags.ORES_IN_GROUND_STONE), "oil in stone");
		h.assertTrue(ModBlocks.DEEPSLATE_NEODYMIUM_ORE.defaultBlockState().is(ConventionalBlockTags.ORES_IN_GROUND_DEEPSLATE),
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
	public void storageBlocksNeedTheirTool(GameTestHelper h) {
		for (Block block : ModBlocks.storageBlocks()) {
			BlockState state = block.defaultBlockState();
			h.assertFalse(ItemStack.EMPTY.isCorrectToolForDrops(state), "bare hand on " + block);
			h.assertFalse(new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(state), "wood on " + block);
			h.assertTrue(new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(state), "iron on " + block);
			List<ItemStack> drops = drops(h, state, new ItemStack(Items.IRON_PICKAXE));
			h.assertValueEqual(drops.size(), 1, block + " one stack");
			h.assertTrue(drops.get(0).is(block.asItem()), block + " drops itself, got " + drops);
		}
		ItemStack stone = new ItemStack(Items.STONE_PICKAXE);
		h.assertTrue(stone.isCorrectToolForDrops(ModBlocks.SILICON_BLOCK.defaultBlockState()), "stone on silicon");
		h.assertFalse(stone.isCorrectToolForDrops(ModBlocks.NEODYMIUM_BLOCK.defaultBlockState()), "stone on neodymium");
		h.assertFalse(stone.isCorrectToolForDrops(ModBlocks.RAW_NEODYMIUM_BLOCK.defaultBlockState()), "stone on raw");
		h.succeed();
	}

	@GameTest
	public void storageBlockTags(GameTestHelper h) {
		List<TagKey<Block>> blockTags = List.of(ModTags.Blocks.STORAGE_BLOCKS_RAW_NEODYMIUM,
				ModTags.Blocks.STORAGE_BLOCKS_NEODYMIUM, ModTags.Blocks.STORAGE_BLOCKS_SILICON);
		List<TagKey<Item>> itemTags = List.of(ModTags.Items.STORAGE_BLOCKS_RAW_NEODYMIUM,
				ModTags.Items.STORAGE_BLOCKS_NEODYMIUM, ModTags.Items.STORAGE_BLOCKS_SILICON);
		List<Block> blocks = ModBlocks.storageBlocks();
		for (int i = 0; i < blocks.size(); i++) {
			Block block = blocks.get(i);
			h.assertTrue(block.defaultBlockState().is(blockTags.get(i)), block + " in " + blockTags.get(i));
			h.assertTrue(block.defaultBlockState().is(ConventionalBlockTags.STORAGE_BLOCKS), block + " in c:storage_blocks");
			h.assertTrue(new ItemStack(block).is(itemTags.get(i)), block + " item in " + itemTags.get(i));
			h.assertTrue(new ItemStack(block).is(ConventionalItemTags.STORAGE_BLOCKS), block + " item in c:storage_blocks");
		}
		h.succeed();
	}

	@GameTest
	public void storageBlockRecipes(GameTestHelper h) {
		RecipeManager recipes = h.getLevel().getServer().getRecipeManager();
		List<ItemLike> contents = List.of(ModItems.RAW_NEODYMIUM, ModItems.NEODYMIUM_INGOT, ModItems.SILICON);
		List<Block> blocks = ModBlocks.storageBlocks();
		for (int i = 0; i < blocks.size(); i++) {
			Block block = blocks.get(i);
			ItemLike content = contents.get(i);
			crafted(h, recipes, name(block), block, CraftingInput.of(3, 3, copies(content, 9)), 1);
			crafted(h, recipes, name(content) + "_from_" + name(block), content,
					CraftingInput.of(1, 1, copies(block, 1)), 9);
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

	@GameTest
	public void overworldBiomesHaveOres(GameTestHelper h) {
		for (ResourceKey<Biome> biome : List.of(Biomes.PLAINS, Biomes.DESERT)) {
			for (ResourceKey<PlacedFeature> ore : List.of(ModWorldgen.PLACED_SHALE, ModWorldgen.PLACED_OIL_SHALE,
					ModWorldgen.PLACED_NEODYMIUM)) {
				h.assertTrue(hasOre(h, biome, ore), biome.identifier() + " has " + ore.identifier());
			}
		}
		h.succeed();
	}

	@GameTest
	public void extraShaleOnlyInDryBiomes(GameTestHelper h) {
		h.assertTrue(hasOre(h, Biomes.DESERT, ModWorldgen.PLACED_OIL_SHALE_EXTRA), "desert has extra oil shale");
		h.assertTrue(hasOre(h, Biomes.BADLANDS, ModWorldgen.PLACED_OIL_SHALE_EXTRA), "badlands has extra oil shale");
		h.assertFalse(hasOre(h, Biomes.PLAINS, ModWorldgen.PLACED_OIL_SHALE_EXTRA), "plains has no extra oil shale");
		h.succeed();
	}

	@GameTest
	public void noOresOutsideOverworld(GameTestHelper h) {
		for (ResourceKey<Biome> biome : List.of(Biomes.NETHER_WASTES, Biomes.THE_END)) {
			for (ResourceKey<PlacedFeature> ore : List.of(ModWorldgen.PLACED_SHALE, ModWorldgen.PLACED_OIL_SHALE,
					ModWorldgen.PLACED_OIL_SHALE_EXTRA, ModWorldgen.PLACED_NEODYMIUM)) {
				h.assertFalse(hasOre(h, biome, ore), biome.identifier() + " has no " + ore.identifier());
			}
		}
		h.succeed();
	}

	@GameTest
	public void oilShaleRunsAfterShale(GameTestHelper h) {
		for (ResourceKey<Biome> biome : List.of(Biomes.PLAINS, Biomes.DESERT)) {
			List<Holder<PlacedFeature>> ores = ores(h, biome);
			int shale = indexOf(ores, ModWorldgen.PLACED_SHALE);
			h.assertTrue(shale >= 0 && shale < indexOf(ores, ModWorldgen.PLACED_OIL_SHALE),
					biome.identifier() + ": shale pockets placed before the oil shale veins");
		}
		h.succeed();
	}

	@GameTest
	public void neodymiumFortuneAndExplosions(GameTestHelper h) {
		BlockState ore = ModBlocks.DEEPSLATE_NEODYMIUM_ORE.defaultBlockState();
		ItemStack fortune = enchanted(h, Enchantments.FORTUNE, 3);
		ItemStack tool = new ItemStack(Items.IRON_PICKAXE);
		int max = 0;
		int exploded = 0;
		for (int i = 0; i < 50; i++) {
			max = Math.max(max, count(drops(h, ore, fortune), ModItems.RAW_NEODYMIUM));
			exploded += count(ore.getDrops(params(h, tool).withParameter(LootContextParams.EXPLOSION_RADIUS, 100.0F)),
					ModItems.RAW_NEODYMIUM);
		}
		h.assertTrue(max > 1, "fortune drops more than one raw neodymium, max " + max);
		h.assertTrue(exploded < 25, "explosion decays raw neodymium, got " + exploded);
		h.succeed();
	}

	private static List<Holder<PlacedFeature>> ores(GameTestHelper h, ResourceKey<Biome> biome) {
		List<HolderSet<PlacedFeature>> steps = h.getLevel().registryAccess().lookupOrThrow(Registries.BIOME)
				.getOrThrow(biome).value().getGenerationSettings().features();
		int step = GenerationStep.Decoration.UNDERGROUND_ORES.ordinal();
		return steps.size() > step ? steps.get(step).stream().toList() : List.of();
	}

	private static int indexOf(List<Holder<PlacedFeature>> features, ResourceKey<PlacedFeature> key) {
		for (int i = 0; i < features.size(); i++) {
			if (features.get(i).is(key)) {
				return i;
			}
		}
		return -1;
	}

	private static boolean hasOre(GameTestHelper h, ResourceKey<Biome> biome, ResourceKey<PlacedFeature> ore) {
		return indexOf(ores(h, biome), ore) >= 0;
	}

	@GameTest
	public void smeltingRecipes(GameTestHelper h) {
		RecipeManager recipes = h.getLevel().getServer().getRecipeManager();
		for (ItemLike input : List.of(ModItems.RAW_NEODYMIUM, ModBlocks.DEEPSLATE_NEODYMIUM_ORE)) {
			cooking(h, recipes, input, ModItems.NEODYMIUM_INGOT, SMELT, 1.0F);
			cooking(h, recipes, input, ModItems.NEODYMIUM_INGOT, BLAST, 1.0F);
		}
		cooking(h, recipes, Items.QUARTZ, ModItems.SILICON, BLAST, 0.5F);
		cooking(h, recipes, ModItems.SILICON, ModItems.SILICON_WAFER, SMELT, 0.5F);
		cooking(h, recipes, ModBlocks.OIL_SHALE, ModItems.BITUMEN, SMELT, 0.1F);
		cooking(h, recipes, ModBlocks.OIL_SHALE, ModItems.BITUMEN, BLAST, 0.1F);
		h.assertTrue(recipe(recipes, "silicon_from_smelting_quartz").isEmpty(), "no smelting for quartz");
		h.assertTrue(recipe(recipes, "silicon_wafer_from_blasting_silicon").isEmpty(), "no blasting for silicon");
		h.succeed();
	}

	@GameTest
	public void shaleRecipes(GameTestHelper h) {
		RecipeManager recipes = h.getLevel().getServer().getRecipeManager();
		crafted(h, recipes, ModBlocks.POLISHED_SHALE, CraftingInput.of(2, 2, copies(ModBlocks.SHALE, 4)), 4);
		crafted(h, recipes, ModBlocks.SHALE_BRICKS, CraftingInput.of(2, 2, copies(ModBlocks.POLISHED_SHALE, 4)), 4);
		crafted(h, recipes, ModBlocks.CHISELED_SHALE, CraftingInput.of(1, 2, copies(ModBlocks.SHALE_BRICK_SLAB, 2)), 1);
		for (Block shape : ModBlocks.shaleFamily()) {
			if (shape instanceof StairBlock || shape instanceof SlabBlock || shape instanceof WallBlock) {
				h.assertTrue(recipe(recipes, name(shape)).isPresent(), shape + " crafting recipe");
			}
		}
		List<Block> family = ModBlocks.shaleFamily();
		stonecut(h, recipes, ModBlocks.SHALE, family.subList(1, family.size()));
		stonecut(h, recipes, ModBlocks.POLISHED_SHALE, family.subList(5, family.size()));
		stonecut(h, recipes, ModBlocks.SHALE_BRICKS, family.subList(9, family.size()));
		h.succeed();
	}

	private static void cooking(GameTestHelper h, RecipeManager recipes, ItemLike input, ItemLike result, Cook cook,
			float xp) {
		String id = name(result) + "_from_" + cook.kind() + "_" + name(input);
		Recipe<?> recipe = recipe(recipes, id).orElseThrow(() -> h.assertionException(id + " missing"));
		h.assertTrue(recipe.getType() == cook.type(), id + " type");
		AbstractCookingRecipe cooking = (AbstractCookingRecipe) recipe;
		h.assertTrue(cooking.assemble(new SingleRecipeInput(new ItemStack(input))).is(result.asItem()), id + " result");
		h.assertValueEqual(cooking.cookingTime(), cook.time(), id + " time");
		h.assertValueEqual(cooking.experience(), xp, id + " xp");
	}

	private static void crafted(GameTestHelper h, RecipeManager recipes, Block result, CraftingInput input, int count) {
		crafted(h, recipes, name(result), result, input, count);
	}

	private static void crafted(GameTestHelper h, RecipeManager recipes, String id, ItemLike result,
			CraftingInput input, int count) {
		Recipe<?> recipe = recipe(recipes, id).orElseThrow(() -> h.assertionException(id + " missing"));
		ItemStack out = ((CraftingRecipe) recipe).assemble(input);
		h.assertTrue(out.is(result.asItem()), result + " result, got " + out);
		h.assertValueEqual(out.getCount(), count, result + " count");
	}

	private static void stonecut(GameTestHelper h, RecipeManager recipes, Block base, List<Block> results) {
		for (Block result : results) {
			String id = name(result) + "_from_" + name(base) + "_stonecutting";
			Recipe<?> recipe = recipe(recipes, id).orElseThrow(() -> h.assertionException(id + " missing"));
			h.assertTrue(recipe.getType() == RecipeType.STONECUTTING, id + " type");
		}
	}

	private static Optional<Recipe<?>> recipe(RecipeManager recipes, String name) {
		return recipes.byKey(ResourceKey.create(Registries.RECIPE, RaveCraft.id(name))).map(RecipeHolder::value);
	}

	private static String name(ItemLike item) {
		return BuiltInRegistries.ITEM.getKey(item.asItem()).getPath();
	}

	private static List<ItemStack> copies(ItemLike item, int n) {
		return Collections.nCopies(n, new ItemStack(item));
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
