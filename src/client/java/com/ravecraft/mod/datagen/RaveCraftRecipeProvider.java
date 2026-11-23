package com.ravecraft.mod.datagen;

import com.ravecraft.mod.registry.ModBlockFamilies;
import com.ravecraft.mod.registry.ModBlocks;
import com.ravecraft.mod.registry.ModItems;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.BlockFamily;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

/** Crafting, cooking and stonecutter recipes. */
public class RaveCraftRecipeProvider extends FabricRecipeProvider {
	private static final int SMELT_TIME = 200;
	private static final int BLAST_TIME = 100;
	private static final float INGOT_XP = 1.0F;
	private static final float SILICON_XP = 0.5F;
	private static final float BITUMEN_XP = 0.1F;

	/**
	 * A provider writing to {@code output}.
	 *
	 * @param output the pack the files go to
	 * @param registries the registries, once loaded
	 */
	public RaveCraftRecipeProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
		super(output, registries);
	}

	@Override
	protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries, BootstrapContext<Recipe<?>> recipes,
			BootstrapContext<Advancement> advancements) {
		return new RecipeProvider(recipes, advancements) {
			@Override
			public void buildRecipes() {
				// PROVISIONAL: replaced by the materials sub-project (docs/SPEC.md).
				shaped(RecipeCategory.REDSTONE, ModBlocks.PORTABLE_SPEAKER)
						.define('F', Items.IRON_INGOT)
						.define('N', Items.NOTE_BLOCK)
						.define('R', Items.REDSTONE)
						.pattern("FFF")
						.pattern("NRN")
						.pattern("FFF")
						.unlockedBy("has_note_block", has(Items.NOTE_BLOCK))
						.save(output);
				// Dyeable like leather armour: the speaker plus any dyes, colours mixed, other components kept.
				dyedItem(ModBlocks.PORTABLE_SPEAKER.asItem(), "portable_speaker_dye");
				// PROVISIONAL as well. The portable speaker is consumed with its queue (no component transfer).
				shaped(RecipeCategory.REDSTONE, ModBlocks.PARTY_SPEAKER)
						.define('I', Items.IRON_INGOT)
						.define('N', Items.NOTE_BLOCK)
						.define('C', Items.COPPER_INGOT)
						.define('P', ModBlocks.PORTABLE_SPEAKER)
						.define('R', Items.REDSTONE_BLOCK)
						.pattern("INI")
						.pattern("CPC")
						.pattern("IRI")
						.unlockedBy("has_portable_speaker", has(ModBlocks.PORTABLE_SPEAKER))
						.save(output);
				dyedItem(ModBlocks.PARTY_SPEAKER.asItem(), "party_speaker_dye");
				materials(this);
				storageBlocks(this);
				for (BlockFamily family : List.of(ModBlockFamilies.SHALE, ModBlockFamilies.POLISHED_SHALE,
						ModBlockFamilies.SHALE_BRICKS)) {
					generateRecipes(family, FeatureFlags.VANILLA_SET);
				}
			}
		};
	}

	/**
	 * Ore smelting: furnace and blast furnace for neodymium and bitumen, blast furnace only for silicon (it needs the
	 * heat), furnace only for the wafer (a slow anneal).
	 */
	private static void materials(RecipeProvider recipes) {
		List<ItemLike> neodymium = List.of(ModItems.RAW_NEODYMIUM, ModBlocks.DEEPSLATE_NEODYMIUM_ORE);
		List<ItemLike> oilShale = List.of(ModBlocks.OIL_SHALE);
		recipes.oreSmelting(neodymium, RecipeCategory.MISC, CookingBookCategory.MISC, ModItems.NEODYMIUM_INGOT,
				INGOT_XP, SMELT_TIME, "neodymium_ingot");
		recipes.oreBlasting(neodymium, RecipeCategory.MISC, CookingBookCategory.MISC, ModItems.NEODYMIUM_INGOT,
				INGOT_XP, BLAST_TIME, "neodymium_ingot");
		recipes.oreBlasting(List.of(Items.QUARTZ), RecipeCategory.MISC, CookingBookCategory.MISC, ModItems.SILICON,
				SILICON_XP, BLAST_TIME, "silicon");
		recipes.oreSmelting(List.of(ModItems.SILICON), RecipeCategory.MISC, CookingBookCategory.MISC,
				ModItems.SILICON_WAFER, SILICON_XP, SMELT_TIME, "silicon_wafer");
		recipes.oreSmelting(oilShale, RecipeCategory.MISC, CookingBookCategory.MISC, ModItems.BITUMEN, BITUMEN_XP,
				SMELT_TIME, "bitumen");
		recipes.oreBlasting(oilShale, RecipeCategory.MISC, CookingBookCategory.MISC, ModItems.BITUMEN, BITUMEN_XP,
				BLAST_TIME, "bitumen");
	}

	/** Nine items to a storage block and back; the unpacking recipe is named {@code <item>_from_<block>}. */
	private static void storageBlocks(RecipeProvider recipes) {
		List<ItemLike> contents = List.of(ModItems.RAW_NEODYMIUM, ModItems.NEODYMIUM_INGOT, ModItems.SILICON);
		List<Block> blocks = ModBlocks.storageBlocks();
		for (int i = 0; i < blocks.size(); i++) {
			ItemLike content = contents.get(i);
			Block block = blocks.get(i);
			recipes.nineBlockStorageRecipesRecipesWithCustomUnpacking(RecipeCategory.MISC, content,
					RecipeCategory.BUILDING_BLOCKS, block,
					RecipeProvider.getItemName(content) + "_from_" + RecipeProvider.getItemName(block),
					RecipeProvider.getItemName(content));
		}
	}
}
