package com.ravecraft.mod.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.recipe.CountedIngredient;
import com.ravecraft.mod.recipe.MachineKind;
import com.ravecraft.mod.recipe.MachineRecipe;
import com.ravecraft.mod.recipe.MachineRecipeInput;
import com.ravecraft.mod.registry.ModItems;
import com.ravecraft.mod.registry.ModRecipes;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

public class MachineGameTest {
	@GameTest
	public void windingRecipesExist(GameTestHelper h) {
		MachineRecipe wire = recipe(h, "copper_wire_from_winding");
		h.assertTrue(wire.kind() == ModRecipes.WINDING, "wire kind");
		h.assertValueEqual(wire.ingredients().size(), 1, "wire ingredients");
		h.assertTrue(wire.ingredients().get(0).ingredient().test(new ItemStack(Items.COPPER_INGOT)), "copper ingot");
		h.assertValueEqual(wire.ingredients().get(0).count(), 1, "one ingot");
		h.assertTrue(wire.result().create().is(ModItems.COPPER_WIRE), "wire result");
		h.assertValueEqual(wire.result().count(), 4, "four wires");
		h.assertValueEqual(wire.processingTime(), 100, "wire time");
		MachineRecipe coil = recipe(h, "voice_coil_from_winding");
		h.assertTrue(coil.ingredients().get(0).ingredient().test(new ItemStack(ModItems.COPPER_WIRE)), "wire input");
		h.assertValueEqual(coil.ingredients().get(0).count(), 4, "four wires in");
		h.assertTrue(coil.result().create().is(ModItems.VOICE_COIL), "coil result");
		h.assertValueEqual(coil.result().count(), 1, "one coil");
		h.succeed();
	}

	@GameTest
	public void windingMatchesAnySlotOrder(GameTestHelper h) {
		MachineRecipe wire = recipe(h, "copper_wire_from_winding");
		ItemStack copper = new ItemStack(Items.COPPER_INGOT);
		h.assertTrue(wire.matches(input(ItemStack.EMPTY, ItemStack.EMPTY, copper), h.getLevel()), "slot 2 matches");
		h.assertFalse(wire.matches(input(new ItemStack(Items.COBBLESTONE), ItemStack.EMPTY, copper), h.getLevel()),
				"stray item");
		Optional<RecipeHolder<MachineRecipe>> found = h.getLevel().recipeAccess()
				.getRecipeFor(ModRecipes.WINDING.type(), input(ItemStack.EMPTY, copper, ItemStack.EMPTY), h.getLevel());
		h.assertTrue(found.isPresent() && found.get().value().result().create().is(ModItems.COPPER_WIRE), "lookup");
		MachineRecipe coil = recipe(h, "voice_coil_from_winding");
		h.assertFalse(coil.matches(input(new ItemStack(ModItems.COPPER_WIRE, 3), ItemStack.EMPTY, ItemStack.EMPTY),
				h.getLevel()), "three wires are not enough");
		h.succeed();
	}

	@GameTest
	public void recipeJsonRoundTrip(GameTestHelper h) {
		MachineKind moulding = new MachineKind("test_moulding", "test_machine", 3, true);
		MachineRecipe recipe = new MachineRecipe(moulding, true,
				List.of(new CountedIngredient(Ingredient.of(Items.COPPER_INGOT), 2),
						new CountedIngredient(Ingredient.of(Items.IRON_INGOT), 1)),
				Optional.of(Ingredient.of(Items.CLAY_BALL)), new ItemStackTemplate(Items.IRON_BARS, 3), 40);
		Codec<MachineRecipe> codec = moulding.serializer().codec().codec();
		RegistryOps<JsonElement> ops = h.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
		JsonElement json = codec.encodeStart(ops, recipe).getOrThrow();
		h.assertTrue(json.getAsJsonObject().has("mold"), "mold written: " + json);
		JsonElement again = codec.encodeStart(ops, codec.parse(ops, json).getOrThrow()).getOrThrow();
		h.assertValueEqual(again, json, "round trip");
		h.succeed();
	}

	@GameTest
	public void moldOnlyWhereNeeded(GameTestHelper h) {
		RegistryOps<JsonElement> ops = h.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
		String withMold = "{\"ingredients\":[{\"ingredient\":\"minecraft:copper_ingot\"}],\"mold\":\"minecraft:clay_ball\","
				+ "\"result\":{\"id\":\"minecraft:iron_bars\"}}";
		String noMold = "{\"ingredients\":[{\"ingredient\":\"minecraft:copper_ingot\",\"count\":2}],"
				+ "\"result\":{\"id\":\"minecraft:iron_bars\"}}";
		h.assertTrue(parse(ModRecipes.WINDING, ops, withMold).isError(), "winding refuses a mold");
		MachineKind moulding = new MachineKind("test_moulding", "test_machine", 3, true);
		h.assertTrue(parse(moulding, ops, noMold).isError(), "moulding needs a mold");
		MachineRecipe parsed = parse(ModRecipes.WINDING, ops, noMold).getOrThrow();
		h.assertValueEqual(parsed.processingTime(), 100, "default processing time");
		h.assertValueEqual(parsed.ingredients().get(0).count(), 2, "count read");
		h.succeed();
	}

	private static DataResult<MachineRecipe> parse(MachineKind kind, RegistryOps<JsonElement> ops, String json) {
		return kind.serializer().codec().codec().parse(ops, JsonParser.parseString(json));
	}

	private static MachineRecipeInput input(ItemStack a, ItemStack b, ItemStack c) {
		return new MachineRecipeInput(List.of(a, b, c), ItemStack.EMPTY);
	}

	private static MachineRecipe recipe(GameTestHelper h, String name) {
		Recipe<?> recipe = h.getLevel().getServer().getRecipeManager()
				.byKey(ResourceKey.create(Registries.RECIPE, RaveCraft.id(name))).map(RecipeHolder::value)
				.orElseThrow(() -> h.assertionException(name + " missing"));
		h.assertTrue(recipe instanceof MachineRecipe, name + " is a machine recipe");
		return (MachineRecipe) recipe;
	}
}
