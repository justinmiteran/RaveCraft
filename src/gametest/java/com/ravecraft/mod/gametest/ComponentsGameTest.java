package com.ravecraft.mod.gametest;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.blockentity.MachineBlockEntity;
import com.ravecraft.mod.recipe.MachineKind;
import com.ravecraft.mod.recipe.MachineRecipe;
import com.ravecraft.mod.registry.ModBlocks;
import com.ravecraft.mod.registry.ModCreativeTabs;
import com.ravecraft.mod.registry.ModItems;
import com.ravecraft.mod.registry.ModRecipes;
import com.ravecraft.mod.registry.ModTags;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.ItemLike;

/** The table-crafted components, their unlock criteria and the reinforced membrane extrusion (spec 3, 6). */
public class ComponentsGameTest {
	private static final BlockPos POS = new BlockPos(1, 2, 1);

	@GameTest
	public void componentCraftsExist(GameTestHelper h) {
		ShapelessRecipe membrane = shapeless(h, "membrane");
		shapelessIngredients(h, "membrane", membrane, Items.PAPER, Items.PAPER, ModItems.PLASTIC_SHEET);
		ItemStack membranes = membrane.assemble(CraftingInput.of(3, 1,
				List.of(new ItemStack(Items.PAPER), new ItemStack(Items.PAPER), new ItemStack(ModItems.PLASTIC_SHEET))));
		h.assertTrue(membranes.is(ModItems.MEMBRANE), "membrane result");
		h.assertValueEqual(membranes.getCount(), 2, "membrane count");
		ShapelessRecipe amplifier = shapeless(h, "amplifier_board");
		shapelessIngredients(h, "amplifier_board", amplifier, ModItems.CHIP, ModItems.SIMPLE_CIRCUIT,
				ModItems.COPPER_WIRE, ModItems.COPPER_WIRE);
		driver(h, "speaker_driver", ModItems.MEMBRANE, ModItems.IRON_MAGNET);
		driver(h, "woofer", ModItems.REINFORCED_MEMBRANE, ModItems.NEODYMIUM_MAGNET);
		h.succeed();
	}

	@GameTest
	public void componentsInTabOrder(GameTestHelper h) {
		h.assertValueEqual(ModItems.components(), List.of(ModItems.COPPER_WIRE, ModItems.VOICE_COIL,
				ModItems.IRON_MAGNET, ModItems.MEMBRANE, ModItems.PLASTIC_SHEET, ModItems.PLASTIC_CASING,
				ModItems.CIRCUIT_BOARD, ModItems.SIMPLE_CIRCUIT, ModItems.SPEAKER_DRIVER, ModItems.NEODYMIUM_MAGNET,
				ModItems.REINFORCED_MEMBRANE, ModItems.CHIP, ModItems.AMPLIFIER_BOARD, ModItems.WOOFER), "tab order");
		h.succeed();
	}

	@GameTest
	public void craftsUnlockByAnyIngredient(GameTestHelper h) {
		criteria(h, "misc/membrane", "has_paper", "has_plastic_sheet");
		criteria(h, "misc/speaker_driver", "has_membrane", "has_iron_ingot", "has_voice_coil", "has_iron_magnet");
		h.succeed();
	}

	@GameTest(maxTicks = 200)
	public void extruderTakesSheetsAsInput(GameTestHelper h) {
		h.setBlock(POS, ModBlocks.EXTRUDER);
		MachineBlockEntity extruder = h.getBlockEntity(POS, MachineBlockEntity.class);
		extruder.setItem(MachineBlockEntity.MOLD, new ItemStack(ModItems.MEMBRANE_MOLD));
		ItemStack sheet = new ItemStack(ModItems.PLASTIC_SHEET);
		h.assertTrue(extruder.canPlaceItem(MachineBlockEntity.INPUT_0, sheet), "sheet goes in the input");
		h.assertFalse(extruder.canPlaceItem(MachineBlockEntity.MOLD, sheet), "sheet is no mold");
		extruder.setItem(MachineBlockEntity.INPUT_0, new ItemStack(ModItems.PLASTIC_SHEET, 2));
		extruder.setItem(MachineBlockEntity.ENERGY, new ItemStack(Items.REDSTONE));
		h.runAfterDelay(110, () -> {
			ItemStack out = extruder.getItem(MachineBlockEntity.OUTPUT);
			h.assertTrue(out.is(ModItems.REINFORCED_MEMBRANE), "reinforced membrane made");
			h.assertValueEqual(out.getCount(), 1, "one membrane");
			h.assertTrue(extruder.getItem(MachineBlockEntity.INPUT_0).isEmpty(), "sheets used");
			h.succeed();
		});
	}

	@GameTest
	public void portableSpeakerCraft(GameTestHelper h) {
		ShapedRecipe recipe = shaped(h, "portable_speaker");
		h.assertValueEqual(recipe.getWidth(), 3, "portable width");
		h.assertValueEqual(recipe.getHeight(), 2, "portable height");
		cells(h, "portable_speaker", recipe, ModItems.SPEAKER_DRIVER, ModItems.PLASTIC_CASING, ModItems.SPEAKER_DRIVER,
				null, ModItems.SIMPLE_CIRCUIT, null);
		h.succeed();
	}

	@GameTest
	public void partySpeakerCraft(GameTestHelper h) {
		ShapedRecipe recipe = shaped(h, "party_speaker");
		cells(h, "party_speaker", recipe, Items.IRON_INGOT, ModItems.WOOFER, Items.IRON_INGOT, ModItems.PLASTIC_CASING,
				ModBlocks.PORTABLE_SPEAKER, ModItems.PLASTIC_CASING, Items.IRON_INGOT, ModItems.AMPLIFIER_BOARD,
				Items.IRON_INGOT);
		criteria(h, "redstone/party_speaker", "has_iron_ingot", "has_woofer", "has_plastic_casing",
				"has_portable_speaker", "has_amplifier_board");
		h.succeed();
	}

	@GameTest
	public void partyAcceptsDyedPortable(GameTestHelper h) {
		ItemStack portable = new ItemStack(ModBlocks.PORTABLE_SPEAKER);
		portable.set(DataComponents.DYED_COLOR, DyedItemColor.applyDyes((DyedItemColor) null, List.of(DyeColor.RED)));
		portable.set(DataComponents.CUSTOM_NAME, Component.literal("Garage"));
		Optional<Ingredient> centre = shaped(h, "party_speaker").getIngredients().get(4);
		h.assertTrue(centre.isPresent() && centre.get().test(portable), "dyed, named portable accepted");
		h.succeed();
	}

	@GameTest
	public void oldSpeakerCraftsGone(GameTestHelper h) {
		ItemStack iron = new ItemStack(Items.IRON_INGOT);
		ItemStack note = new ItemStack(Items.NOTE_BLOCK);
		h.assertTrue(crafts(h, iron, iron, iron, note, new ItemStack(Items.REDSTONE), note, iron, iron, iron).isEmpty(),
				"old portable craft gone");
		h.assertTrue(crafts(h, iron, note, iron, new ItemStack(Items.COPPER_INGOT),
				new ItemStack(ModBlocks.PORTABLE_SPEAKER), new ItemStack(Items.COPPER_INGOT), iron,
				new ItemStack(Items.REDSTONE_BLOCK), iron).isEmpty(), "old party craft gone");
		h.succeed();
	}

	private static Optional<?> crafts(GameTestHelper h, ItemStack... grid) {
		CraftingInput input = CraftingInput.of(3, 3, List.of(grid));
		return h.getLevel().recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, h.getLevel());
	}

	@GameTest
	public void blankMoldIsNoMold(GameTestHelper h) {
		ItemStack blank = new ItemStack(ModItems.BLANK_MOLD);
		h.assertValueEqual(blank.getMaxStackSize(), 16, "blank mold stack size");
		h.assertFalse(blank.is(ModTags.Items.MOLDS), "blank mold not in the molds tag");
		CreativeModeTabs.tryRebuildTabContents(h.getLevel().enabledFeatures(), false, h.getLevel().registryAccess());
		h.assertTrue(ModCreativeTabs.RAVECRAFT.contains(blank), "blank mold in the tab");
		h.setBlock(POS, ModBlocks.EXTRUDER);
		MachineBlockEntity extruder = h.getBlockEntity(POS, MachineBlockEntity.class);
		h.assertFalse(extruder.canPlaceItem(MachineBlockEntity.MOLD, blank), "extruder refuses the blank mold");
		ShapedRecipe recipe = shaped(h, "blank_mold");
		cells(h, "blank_mold", recipe, null, Items.IRON_INGOT, null, Items.IRON_INGOT, Items.CLAY_BALL,
				Items.IRON_INGOT, null, Items.IRON_INGOT, null);
		criteria(h, "misc/blank_mold", "has_iron_ingot", "has_clay_ball");
		h.succeed();
	}

	@GameTest
	public void everyMoldIsUsed(GameTestHelper h) {
		List<MachineRecipe> recipes = machineRecipes(h, ModRecipes.EXTRUDING);
		for (Item mold : ModItems.molds()) {
			ItemStack stack = new ItemStack(mold);
			h.assertTrue(recipes.stream().anyMatch(recipe -> recipe.mold().orElseThrow().test(stack)), mold + " has a recipe");
		}
		h.succeed();
	}

	private static List<MachineRecipe> machineRecipes(GameTestHelper h, MachineKind kind) {
		List<MachineRecipe> found = new ArrayList<>();
		for (RecipeHolder<?> holder : h.getLevel().getServer().getRecipeManager().getRecipes()) {
			if (holder.value() instanceof MachineRecipe recipe && recipe.kind() == kind) {
				found.add(recipe);
			}
		}
		h.assertFalse(found.isEmpty(), kind.machine() + " has recipes");
		return found;
	}

	/** The speaker driver pattern: membrane on top, iron, voice coil and iron in the middle, magnet below. */
	private static void driver(GameTestHelper h, String name, Item membrane, Item magnet) {
		ShapedRecipe recipe = shaped(h, name);
		h.assertValueEqual(recipe.getWidth(), 3, name + " width");
		h.assertValueEqual(recipe.getHeight(), 3, name + " height");
		cells(h, name, recipe, null, membrane, null, Items.IRON_INGOT, ModItems.VOICE_COIL, Items.IRON_INGOT, null,
				magnet, null);
	}

	/**
	 * Checks a shaped recipe cell by cell; a null item means an empty cell.
	 *
	 * @param h the test helper
	 * @param name the recipe name, for messages
	 * @param recipe the recipe
	 * @param items the expected item of each cell, row by row
	 */
	static void cells(GameTestHelper h, String name, ShapedRecipe recipe, ItemLike... items) {
		List<Optional<Ingredient>> ingredients = recipe.getIngredients();
		h.assertValueEqual(ingredients.size(), items.length, name + " cells");
		for (int i = 0; i < items.length; i++) {
			Optional<Ingredient> cell = ingredients.get(i);
			if (items[i] == null) {
				h.assertTrue(cell.isEmpty(), name + " cell " + i + " empty");
			} else {
				h.assertTrue(cell.isPresent() && cell.get().test(new ItemStack(items[i])), name + " cell " + i);
			}
		}
	}

	private static void shapelessIngredients(GameTestHelper h, String name, ShapelessRecipe recipe,
			ItemLike... items) {
		List<Ingredient> ingredients = recipe.placementInfo().ingredients();
		h.assertValueEqual(ingredients.size(), items.length, name + " ingredients");
		for (int i = 0; i < items.length; i++) {
			h.assertTrue(ingredients.get(i).test(new ItemStack(items[i])), name + " ingredient " + i);
		}
	}

	/**
	 * Checks that a recipe's advancement unlocks with any one of the given criteria, plus the recipe itself.
	 *
	 * @param h the test helper
	 * @param path the recipe's path under {@code recipes/}, such as {@code misc/membrane}
	 * @param criteria the expected {@code has_*} criteria
	 */
	static void criteria(GameTestHelper h, String path, String... criteria) {
		AdvancementHolder holder = h.getLevel().getServer().getAdvancements().get(RaveCraft.id("recipes/" + path));
		h.assertTrue(holder != null, path + " advancement");
		Set<String> expected = new HashSet<>(List.of(criteria));
		expected.add("has_the_recipe");
		h.assertValueEqual(holder.value().criteria().keySet(), expected, path + " criteria");
		List<List<String>> requirements = holder.value().requirements().requirements();
		h.assertValueEqual(requirements.size(), 1, path + " one or-group");
		h.assertValueEqual(Set.copyOf(requirements.get(0)), expected, path + " or-group");
	}

	/**
	 * Loads a shaped crafting recipe of the mod.
	 *
	 * @param h the test helper
	 * @param name the recipe path
	 * @return the recipe
	 */
	static ShapedRecipe shaped(GameTestHelper h, String name) {
		Recipe<?> recipe = load(h, name);
		h.assertTrue(recipe instanceof ShapedRecipe, name + " is shaped");
		return (ShapedRecipe) recipe;
	}

	/**
	 * Loads a shapeless crafting recipe of the mod.
	 *
	 * @param h the test helper
	 * @param name the recipe path
	 * @return the recipe
	 */
	static ShapelessRecipe shapeless(GameTestHelper h, String name) {
		Recipe<?> recipe = load(h, name);
		h.assertTrue(recipe instanceof ShapelessRecipe, name + " is shapeless");
		return (ShapelessRecipe) recipe;
	}

	private static Recipe<?> load(GameTestHelper h, String name) {
		return h.getLevel().getServer().getRecipeManager()
				.byKey(ResourceKey.create(Registries.RECIPE, RaveCraft.id(name))).map(RecipeHolder::value)
				.orElseThrow(() -> h.assertionException(name + " missing"));
	}
}
