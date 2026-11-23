package com.ravecraft.mod.gametest;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.recipe.MachineKind;
import com.ravecraft.mod.recipe.MachineRecipe;
import com.ravecraft.mod.recipe.MachineRecipeInput;
import com.ravecraft.mod.registry.ModItems;
import com.ravecraft.mod.registry.ModRecipes;
import com.ravecraft.mod.registry.ModTags;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;

/** The magnetizing, etching and extruding recipes, their items and the molds (spec 5.2, 6.1). */
public class MachineRecipesGameTest {
	@GameTest
	public void machineRecipesExist(GameTestHelper h) {
		check(h, "iron_magnet_from_magnetizing", ModRecipes.MAGNETIZING, null, out(ModItems.IRON_MAGNET, 1),
				Items.IRON_INGOT, 1, Items.REDSTONE, 1);
		check(h, "neodymium_magnet_from_magnetizing", ModRecipes.MAGNETIZING, null, out(ModItems.NEODYMIUM_MAGNET, 2),
				ModItems.NEODYMIUM_INGOT, 1, Items.IRON_INGOT, 1);
		check(h, "simple_circuit_from_etching", ModRecipes.ETCHING, null, out(ModItems.SIMPLE_CIRCUIT, 1),
				ModItems.CIRCUIT_BOARD, 1, ModItems.COPPER_WIRE, 2, Items.REDSTONE, 1);
		check(h, "chip_from_etching", ModRecipes.ETCHING, null, out(ModItems.CHIP, 1),
				ModItems.SILICON_WAFER, 1, Items.GOLD_NUGGET, 2, Items.REDSTONE, 1);
		check(h, "plastic_sheet_from_extruding", ModRecipes.EXTRUDING, ModItems.SHEET_MOLD,
				out(ModItems.PLASTIC_SHEET, 2), ModItems.BITUMEN, 1);
		check(h, "plastic_casing_from_extruding", ModRecipes.EXTRUDING, ModItems.CASING_MOLD,
				out(ModItems.PLASTIC_CASING, 1), ModItems.BITUMEN, 3);
		check(h, "reinforced_membrane_from_extruding", ModRecipes.EXTRUDING, ModItems.MEMBRANE_MOLD,
				out(ModItems.REINFORCED_MEMBRANE, 1), ModItems.PLASTIC_SHEET, 2);
		check(h, "circuit_board_from_extruding", ModRecipes.EXTRUDING, ModItems.BOARD_MOLD,
				out(ModItems.CIRCUIT_BOARD, 2), ModItems.BITUMEN, 1);
		h.succeed();
	}

	@GameTest
	public void extrudingNeedsItsMold(GameTestHelper h) {
		ItemStack bitumen = new ItemStack(ModItems.BITUMEN);
		h.assertTrue(extruded(h, bitumen, ModItems.SHEET_MOLD).filter(s -> s.is(ModItems.PLASTIC_SHEET)).isPresent(),
				"sheet mold makes sheets");
		h.assertTrue(extruded(h, bitumen, ModItems.BOARD_MOLD).filter(s -> s.is(ModItems.CIRCUIT_BOARD)).isPresent(),
				"board mold makes boards");
		h.assertTrue(extruded(h, bitumen, null).isEmpty(), "no mold, no recipe");
		h.assertTrue(extruded(h, bitumen, ModItems.CASING_MOLD).isEmpty(), "one bitumen is not enough for a casing");
		h.succeed();
	}

	@GameTest
	public void kindsHaveTheirInputs(GameTestHelper h) {
		h.assertValueEqual(ModRecipes.MAGNETIZING.inputs(), 2, "magnetizer inputs");
		h.assertValueEqual(ModRecipes.ETCHING.inputs(), 3, "etcher inputs");
		h.assertValueEqual(ModRecipes.EXTRUDING.inputs(), 1, "extruder inputs");
		h.assertFalse(ModRecipes.MAGNETIZING.needsMold() || ModRecipes.ETCHING.needsMold(), "no mold");
		h.assertTrue(ModRecipes.EXTRUDING.needsMold(), "extruder mold");
		h.assertValueEqual(ModRecipes.EXTRUDING.machine(), "extruder", "machine id");
		h.succeed();
	}

	@GameTest
	public void moldsAreToolsInTheTag(GameTestHelper h) {
		h.assertValueEqual(ModItems.molds().size(), 4, "four molds");
		for (Item mold : ModItems.molds()) {
			ItemStack stack = new ItemStack(mold);
			h.assertValueEqual(stack.getMaxStackSize(), 1, mold + " stack size");
			h.assertTrue(stack.is(ModTags.Items.MOLDS), mold + " in the molds tag");
		}
		h.assertFalse(new ItemStack(ModItems.BITUMEN).is(ModTags.Items.MOLDS), "bitumen is no mold");
		h.succeed();
	}

	@GameTest
	public void moldCraftsExist(GameTestHelper h) {
		for (Item mold : ModItems.molds()) {
			String name = mold.builtInRegistryHolder().key().identifier().getPath();
			Optional<RecipeHolder<?>> found = h.getLevel().getServer().getRecipeManager()
					.byKey(ResourceKey.create(Registries.RECIPE, RaveCraft.id(name)));
			h.assertTrue(found.isPresent() && found.get().value() instanceof ShapedRecipe, name + " craft");
		}
		h.succeed();
	}

	private static Optional<ItemStack> extruded(GameTestHelper h, ItemStack input, Item mold) {
		MachineRecipeInput in = new MachineRecipeInput(List.of(input.copy(), ItemStack.EMPTY, ItemStack.EMPTY),
				mold == null ? ItemStack.EMPTY : new ItemStack(mold));
		return h.getLevel().recipeAccess().getRecipeFor(ModRecipes.EXTRUDING.type(), in, h.getLevel())
				.map(holder -> holder.value().assemble(in));
	}

	private static ItemStack out(Item item, int count) {
		return new ItemStack(item, count);
	}

	/** Checks a recipe: kind, mold, result with its count, then (item, count) pairs for the ingredients in order. */
	private static void check(GameTestHelper h, String name, MachineKind kind, Item mold, ItemStack result,
			Object... inputs) {
		MachineRecipe recipe = MachineGameTest.recipe(h, name);
		h.assertTrue(recipe.kind() == kind, name + " kind");
		h.assertValueEqual(recipe.ingredients().size(), inputs.length / 2, name + " ingredients");
		for (int i = 0; i < inputs.length / 2; i++) {
			Item item = (Item) inputs[2 * i];
			h.assertTrue(recipe.ingredients().get(i).ingredient().test(new ItemStack(item)), name + " input " + i);
			h.assertValueEqual(recipe.ingredients().get(i).count(), (Integer) inputs[2 * i + 1], name + " count " + i);
		}
		h.assertValueEqual(recipe.mold().isPresent(), mold != null, name + " has a mold");
		if (mold != null) {
			h.assertTrue(recipe.mold().get().test(new ItemStack(mold)), name + " mold");
		}
		h.assertTrue(recipe.result().create().is(result.getItem()), name + " result");
		h.assertValueEqual(recipe.result().count(), result.getCount(), name + " result count");
		h.assertValueEqual(recipe.processingTime(), 100, name + " time");
	}
}
