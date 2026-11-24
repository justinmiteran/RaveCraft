package com.ravecraft.mod.datagen;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.core.machine.MachineConstants;
import com.ravecraft.mod.recipe.MachineEnergy;
import com.ravecraft.mod.registry.ModBlockFamilies;
import com.ravecraft.mod.registry.ModBlocks;
import com.ravecraft.mod.registry.ModItems;
import com.ravecraft.mod.registry.ModRecipes;
import com.ravecraft.mod.registry.ModTags;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.triggers.Criterion;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.data.BlockFamily;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.ItemLike;

/** Crafting, cooking, stonecutter and machine recipes. */
public class RaveCraftRecipeProvider extends FabricRecipeProvider {
	private static final int SMELT_TIME = 200;
	private static final int BLAST_TIME = 100;
	private static final float INGOT_XP = 1.0F;
	private static final float SILICON_XP = 0.5F;
	private static final float BITUMEN_XP = 0.1F;
	private static final int STORAGE_COUNT = 9;
	private static final int WIRES_PER_INGOT = 4;
	private static final int WIRES_PER_COIL = 4;

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
			machineEnergy();
			machineRecipes();
			components();
			machineCrafts();
			for (BlockFamily family : List.of(ModBlockFamilies.SHALE, ModBlockFamilies.POLISHED_SHALE,
					ModBlockFamilies.SHALE_BRICKS)) {
				generateRecipes(family, FeatureFlags.VANILLA_SET);
			}
		}

		/** The speakers, from components (spec 6.1). Both are dyeable. */
		private void speakers() {
			shapedCraft(RecipeCategory.REDSTONE, ModBlocks.PORTABLE_SPEAKER, "DBS",
					new Key[] {key(ModItems.SPEAKER_DRIVER), key(ModItems.PLASTIC_CASING), key(ModItems.SIMPLE_CIRCUIT)},
					"DBD", " S ");
			// Dyeable like leather armour: the speaker plus any dyes, colours mixed, other components kept.
			dyedItem(ModBlocks.PORTABLE_SPEAKER.asItem(), "portable_speaker_dye");
			// The portable speaker is consumed with its queue (no component transfer).
			shapedCraft(RecipeCategory.REDSTONE, ModBlocks.PARTY_SPEAKER, "IWBPA", new Key[] {iron(),
					key(ModItems.WOOFER), key(ModItems.PLASTIC_CASING), key(ModBlocks.PORTABLE_SPEAKER),
					key(ModItems.AMPLIFIER_BOARD)}, "IWI", "BPB", "IAI");
			dyedItem(ModBlocks.PARTY_SPEAKER.asItem(), "party_speaker_dye");
		}

		/** The machines, the blank mold and the molds (spec 6.2). */
		private void machineCrafts() {
			shapedCraft(RecipeCategory.MISC, ModBlocks.COIL_WINDER, "ICR", new Key[] {iron(),
					key(ConventionalItemTags.COPPER_INGOTS, Items.COPPER_INGOT), redstone()}, "III", "CRC", "III");
			shapedCraft(RecipeCategory.MISC, ModBlocks.MAGNETIZER, "INCR", new Key[] {iron(),
					key(ModTags.Items.INGOTS_NEODYMIUM, ModItems.NEODYMIUM_INGOT),
					key(ConventionalItemTags.COPPER_INGOTS, Items.COPPER_INGOT), redstone()}, "INI", "CRC", "INI");
			shapedCraft(RecipeCategory.MISC, ModBlocks.ETCHER, "IGRA", new Key[] {iron(),
					key(ConventionalItemTags.GLASS_BLOCKS_COLORLESS, Items.GLASS), redstone(),
					key(ConventionalItemTags.AMETHYST_GEMS, Items.AMETHYST_SHARD)}, "IGI", "RAR", "III");
			shapedCraft(RecipeCategory.MISC, ModBlocks.EXTRUDER, "IPH",
					new Key[] {iron(), key(Items.PISTON), key(Items.BLAST_FURNACE)}, "IPI", "IHI", "III");
			shapedCraft(RecipeCategory.MISC, ModItems.BLANK_MOLD, "IK", new Key[] {iron(), key(Items.CLAY_BALL)},
					" I ", "IKI", " I ");
			mold(ModItems.SHEET_MOLD, key(Items.HEAVY_WEIGHTED_PRESSURE_PLATE));
			mold(ModItems.CASING_MOLD, key(ConventionalItemTags.STORAGE_BLOCKS_IRON, Items.IRON_BLOCK));
			mold(ModItems.MEMBRANE_MOLD, key(ModItems.MEMBRANE));
			mold(ModItems.BOARD_MOLD, key(ModItems.PLASTIC_SHEET));
		}

		/** The table-crafted components (spec 6.1). */
		private void components() {
			Key paper = key(Items.PAPER);
			shapelessCraft(ModItems.MEMBRANE, 2, paper, paper, key(ModItems.PLASTIC_SHEET));
			shapedCraft(RecipeCategory.MISC, ModItems.SPEAKER_DRIVER, "MIVG", new Key[] {key(ModItems.MEMBRANE),
					iron(), key(ModItems.VOICE_COIL), key(ModItems.IRON_MAGNET)}, " M ", "IVI", " G ");
			shapedCraft(RecipeCategory.MISC, ModItems.WOOFER, "MIVG", new Key[] {key(ModItems.REINFORCED_MEMBRANE),
					iron(), key(ModItems.VOICE_COIL), key(ModItems.NEODYMIUM_MAGNET)}, " M ", "IVI", " G ");
			Key wire = key(ModItems.COPPER_WIRE);
			shapelessCraft(ModItems.AMPLIFIER_BOARD, 1, key(ModItems.CHIP), key(ModItems.SIMPLE_CIRCUIT), wire, wire);
		}

		/** What the machines make (spec 5.2). */
		private void machineRecipes() {
			MachineRecipeBuilder.machine(ModRecipes.WINDING, ModItems.COPPER_WIRE, WIRES_PER_INGOT, this)
					.input(ConventionalItemTags.COPPER_INGOTS, 1).save(output);
			MachineRecipeBuilder.machine(ModRecipes.WINDING, ModItems.VOICE_COIL, 1, this)
					.input(ModItems.COPPER_WIRE, WIRES_PER_COIL).save(output);
			MachineRecipeBuilder.machine(ModRecipes.MAGNETIZING, ModItems.IRON_MAGNET, 1, this)
					.input(ConventionalItemTags.IRON_INGOTS, 1).input(ConventionalItemTags.REDSTONE_DUSTS, 1)
					.save(output);
			MachineRecipeBuilder.machine(ModRecipes.MAGNETIZING, ModItems.NEODYMIUM_MAGNET, 2, this)
					.input(ModTags.Items.INGOTS_NEODYMIUM, 1).input(ConventionalItemTags.IRON_INGOTS, 1).save(output);
			MachineRecipeBuilder.machine(ModRecipes.ETCHING, ModItems.SIMPLE_CIRCUIT, 1, this)
					.input(ModItems.CIRCUIT_BOARD, 1).input(ModItems.COPPER_WIRE, 2)
					.input(ConventionalItemTags.REDSTONE_DUSTS, 1).save(output);
			MachineRecipeBuilder.machine(ModRecipes.ETCHING, ModItems.CHIP, 1, this)
					.input(ModItems.SILICON_WAFER, 1).input(ConventionalItemTags.GOLD_NUGGETS, 2)
					.input(ConventionalItemTags.REDSTONE_DUSTS, 1).save(output);
			extrusion(ModItems.BITUMEN, 1, ModItems.PLASTIC_SHEET, 2, ModItems.SHEET_MOLD);
			extrusion(ModItems.BITUMEN, 3, ModItems.PLASTIC_CASING, 1, ModItems.CASING_MOLD);
			extrusion(ModItems.PLASTIC_SHEET, 2, ModItems.REINFORCED_MEMBRANE, 1, ModItems.MEMBRANE_MOLD);
			extrusion(ModItems.BITUMEN, 1, ModItems.CIRCUIT_BOARD, 2, ModItems.BOARD_MOLD);
		}

		private void extrusion(ItemLike input, int inputCount, ItemLike result, int count, ItemLike mold) {
			MachineRecipeBuilder.machine(ModRecipes.EXTRUDING, result, count, this)
					.input(input, inputCount).mold(mold).save(output);
		}

		/**
		 * A shaped craft: each symbol stands for the key at the same index, and each key unlocks it (spec 6.2).
		 */
		private void shapedCraft(RecipeCategory category, ItemLike result, String symbols, Key[] keys,
				String... rows) {
			ShapedRecipeBuilder builder = shaped(category, result);
			for (int i = 0; i < keys.length; i++) {
				builder.define(symbols.charAt(i), keys[i].ingredient());
			}
			for (String row : rows) {
				builder.pattern(row);
			}
			for (Key key : keys) {
				builder.unlockedBy(key.criterionName(), key.criterion());
			}
			builder.save(output);
		}

		/** A shapeless craft: a repeated key counts twice, and each distinct key unlocks it (spec 6.2). */
		private void shapelessCraft(ItemLike result, int count, Key... keys) {
			ShapelessRecipeBuilder builder = shapeless(RecipeCategory.MISC, result, count);
			Set<String> seen = new HashSet<>();
			for (Key key : keys) {
				builder.requires(key.ingredient());
				if (seen.add(key.criterionName())) {
					builder.unlockedBy(key.criterionName(), key.criterion());
				}
			}
			builder.save(output);
		}

		/** A mold: a blank mold and its shaping item, unlocked by either. */
		private void mold(ItemLike mold, Key shape) {
			shapelessCraft(mold, 1, key(ModItems.BLANK_MOLD), shape);
		}

		private Key iron() {
			return key(ConventionalItemTags.IRON_INGOTS, Items.IRON_INGOT);
		}

		private Key redstone() {
			return key(ConventionalItemTags.REDSTONE_DUSTS, Items.REDSTONE);
		}

		/** A key asking for anything in {@code tag}; its criterion is named after the vanilla {@code named} item. */
		private Key key(TagKey<Item> tag, ItemLike named) {
			return new Key(tag(tag), getHasName(named), has(tag));
		}

		private Key key(ItemLike item) {
			return new Key(Ingredient.of(item), getHasName(item), has(item));
		}

		/** The default machine energy: redstone dust and blocks, saved as {@code machine_energy/<item>}. */
		private void machineEnergy() {
			energy(ConventionalItemTags.REDSTONE_DUSTS, Items.REDSTONE, MachineConstants.OPERATIONS_PER_DUST);
			energy(ConventionalItemTags.STORAGE_BLOCKS_REDSTONE, Items.REDSTONE_BLOCK,
					MachineConstants.OPERATIONS_PER_BLOCK);
		}

		private void energy(TagKey<Item> fuel, ItemLike named, int operations) {
			String name = "machine_energy/" + getItemName(named);
			output.accept(ResourceKey.create(Registries.RECIPE, RaveCraft.id(name)),
					new MachineEnergy(tag(fuel), operations), null);
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

	/** A crafting key: what it accepts, and the criterion that unlocks the recipe. */
	private record Key(Ingredient ingredient, String criterionName, Criterion<?> criterion) {
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
