package com.ravecraft.mod.datagen;

import com.ravecraft.mod.registry.ModBlockFamilies;
import com.ravecraft.mod.registry.ModBlocks;
import com.ravecraft.mod.registry.ModItems;
import com.ravecraft.mod.registry.ModTags;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.data.BlockFamily;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.ItemLike;

/** Crafting, cooking and stonecutter recipes. */
public class RaveCraftRecipeProvider extends FabricRecipeProvider {
	private static final int SMELT_TIME = 200;
	private static final int BLAST_TIME = 100;
	private static final float INGOT_XP = 1.0F;
	private static final float SILICON_XP = 0.5F;
	private static final float BITUMEN_XP = 0.1F;
	private static final int STORAGE_COUNT = 9;

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
		return new Recipes(recipes, advancements);
	}

	/**
	 * The recipes. Ingredients that other mods also make are asked for by convention tag ({@code c:}), so their iron,
	 * copper or neodymium works here too.
	 */
	private static final class Recipes extends RecipeProvider {
		Recipes(BootstrapContext<Recipe<?>> recipes, BootstrapContext<Advancement> advancements) {
			super(recipes, advancements);
		}

		@Override
		public void buildRecipes() {
			speakers();
			materials();
			storageBlocks();
			for (BlockFamily family : List.of(ModBlockFamilies.SHALE, ModBlockFamilies.POLISHED_SHALE,
					ModBlockFamilies.SHALE_BRICKS)) {
				generateRecipes(family, FeatureFlags.VANILLA_SET);
			}
		}

		/** PROVISIONAL: replaced by the materials sub-project (docs/SPEC.md). Both speakers are dyeable. */
		private void speakers() {
			shaped(RecipeCategory.REDSTONE, ModBlocks.PORTABLE_SPEAKER)
					.define('F', ConventionalItemTags.IRON_INGOTS)
					.define('N', Items.NOTE_BLOCK)
					.define('R', ConventionalItemTags.REDSTONE_DUSTS)
					.pattern("FFF")
					.pattern("NRN")
					.pattern("FFF")
					.unlockedBy("has_note_block", has(Items.NOTE_BLOCK))
					.save(output);
			// Dyeable like leather armour: the speaker plus any dyes, colours mixed, other components kept.
			dyedItem(ModBlocks.PORTABLE_SPEAKER.asItem(), "portable_speaker_dye");
			// The portable speaker is consumed with its queue (no component transfer).
			shaped(RecipeCategory.REDSTONE, ModBlocks.PARTY_SPEAKER)
					.define('I', ConventionalItemTags.IRON_INGOTS)
					.define('N', Items.NOTE_BLOCK)
					.define('C', ConventionalItemTags.COPPER_INGOTS)
					.define('P', ModBlocks.PORTABLE_SPEAKER)
					.define('R', ConventionalItemTags.STORAGE_BLOCKS_REDSTONE)
					.pattern("INI")
					.pattern("CPC")
					.pattern("IRI")
					.unlockedBy("has_portable_speaker", has(ModBlocks.PORTABLE_SPEAKER))
					.save(output);
			dyedItem(ModBlocks.PARTY_SPEAKER.asItem(), "party_speaker_dye");
		}

		/**
		 * Ore smelting: furnace and blast furnace for neodymium and bitumen, blast furnace only for silicon (it needs
		 * the heat), furnace only for the wafer (a slow anneal).
		 */
		private void materials() {
			for (Cooker cooker : List.of(Cooker.SMELT, Cooker.BLAST)) {
				cook(cooker, ModTags.Items.RAW_MATERIALS_NEODYMIUM, ModItems.RAW_NEODYMIUM, ModItems.NEODYMIUM_INGOT,
						INGOT_XP);
				cook(cooker, ModTags.Items.ORES_NEODYMIUM, ModBlocks.DEEPSLATE_NEODYMIUM_ORE, ModItems.NEODYMIUM_INGOT,
						INGOT_XP);
				cook(cooker, ModTags.Items.ORES_OIL_SHALE, ModBlocks.OIL_SHALE, ModItems.BITUMEN, BITUMEN_XP);
			}
			cook(Cooker.BLAST, ConventionalItemTags.QUARTZ_GEMS, Items.QUARTZ, ModItems.SILICON, SILICON_XP);
			cook(Cooker.SMELT, ModTags.Items.SILICON, ModItems.SILICON, ModItems.SILICON_WAFER, SILICON_XP);
		}

		/**
		 * Cooks anything in {@code input} into {@code result}, in a recipe named after {@code named} the way vanilla
		 * names ore cooking: {@code <result>_from_<smelting|blasting>_<named>}.
		 */
		private void cook(Cooker cooker, TagKey<Item> input, ItemLike named, ItemLike result, float xp) {
			SimpleCookingRecipeBuilder.generic(tag(input), RecipeCategory.MISC, CookingBookCategory.MISC, result, xp,
					cooker.time, cooker.factory)
					.group(getItemName(result))
					.unlockedBy(getHasName(named), has(input))
					.save(output, getItemName(result) + "_from_" + cooker.kind + "_" + getItemName(named));
		}

		/** Nine items to a storage block and back; the unpacking recipe is named {@code <item>_from_<block>}. */
		private void storageBlocks() {
			pack(ModTags.Items.RAW_MATERIALS_NEODYMIUM, ModItems.RAW_NEODYMIUM,
					ModTags.Items.STORAGE_BLOCKS_RAW_NEODYMIUM, ModBlocks.RAW_NEODYMIUM_BLOCK);
			pack(ModTags.Items.INGOTS_NEODYMIUM, ModItems.NEODYMIUM_INGOT, ModTags.Items.STORAGE_BLOCKS_NEODYMIUM,
					ModBlocks.NEODYMIUM_BLOCK);
			pack(ModTags.Items.SILICON, ModItems.SILICON, ModTags.Items.STORAGE_BLOCKS_SILICON, ModBlocks.SILICON_BLOCK);
		}

		/** Packs nine of {@code contentTag} into {@code block}, and unpacks any {@code blockTag} into nine content. */
		private void pack(TagKey<Item> contentTag, ItemLike content, TagKey<Item> blockTag, ItemLike block) {
			shapeless(RecipeCategory.MISC, content, STORAGE_COUNT)
					.requires(blockTag)
					.group(getItemName(content))
					.unlockedBy(getHasName(block), has(blockTag))
					.save(output, getItemName(content) + "_from_" + getItemName(block));
			shaped(RecipeCategory.BUILDING_BLOCKS, block)
					.define('#', contentTag)
					.pattern("###")
					.pattern("###")
					.pattern("###")
					.unlockedBy(getHasName(content), has(contentTag))
					.save(output);
		}
	}

	/** A cooking station: its recipe, its name in recipe ids and its time. */
	private enum Cooker {
		SMELT(SmeltingRecipe::new, "smelting", SMELT_TIME),
		BLAST(BlastingRecipe::new, "blasting", BLAST_TIME);

		private final AbstractCookingRecipe.Factory<?> factory;
		private final String kind;
		private final int time;

		Cooker(AbstractCookingRecipe.Factory<?> factory, String kind, int time) {
			this.factory = factory;
			this.kind = kind;
			this.time = time;
		}
	}
}
