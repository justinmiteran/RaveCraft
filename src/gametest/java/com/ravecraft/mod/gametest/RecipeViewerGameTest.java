package com.ravecraft.mod.gametest;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.ravecraft.mod.blockentity.MachineBlockEntity;
import com.ravecraft.mod.compat.rei.MachineDisplayData;
import com.ravecraft.mod.menu.MachineMenu;
import com.ravecraft.mod.recipe.CountedIngredient;
import com.ravecraft.mod.recipe.MachineKind;
import com.ravecraft.mod.recipe.MachineRecipe;
import com.ravecraft.mod.registry.ModRecipes;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.impl.recipe.sync.RecipeSyncImpl;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class RecipeViewerGameTest {
	private static final int MIN_RECIPES = 10;

	@GameTest
	public void machineRecipesSyncToClients(GameTestHelper h) {
		h.assertValueEqual(ModRecipes.kinds().size(), 4, "four machines");
		for (MachineKind kind : ModRecipes.kinds()) {
			h.assertTrue(RecipeSyncImpl.isSynced(kind.serializer()), kind + " recipes reach the client");
		}
		h.succeed();
	}

	@GameTest
	public void everyMachineRecipeSurvivesTheNetwork(GameTestHelper h) {
		for (RecipeHolder<MachineRecipe> holder : recipes(h)) {
			MachineKind kind = holder.value().kind();
			RegistryFriendlyByteBuf buf = buffer(h);
			kind.serializer().streamCodec().encode(buf, holder.value());
			assertSame(h, holder.id() + "", holder.value(), kind.serializer().streamCodec().decode(buf));
		}
		h.succeed();
	}

	@GameTest
	public void displayDataSurvivesTheNetwork(GameTestHelper h) {
		for (RecipeHolder<MachineRecipe> holder : recipes(h)) {
			MachineDisplayData data = MachineDisplayData.of(holder);
			RegistryFriendlyByteBuf buf = buffer(h);
			MachineDisplayData.STREAM_CODEC.encode(buf, data);
			MachineDisplayData copy = MachineDisplayData.STREAM_CODEC.decode(buf);
			h.assertValueEqual(buf.readableBytes(), 0, data.id() + " bytes left");
			h.assertValueEqual(copy.id(), holder.id().identifier(), "recipe id");
			assertSame(h, data.id() + "", holder.value(), copy.recipe());
		}
		h.succeed();
	}

	@GameTest
	public void displayDataSurvivesJson(GameTestHelper h) {
		Codec<MachineDisplayData> codec = MachineDisplayData.MAP_CODEC.codec();
		DynamicOps<JsonElement> ops = h.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
		for (RecipeHolder<MachineRecipe> holder : recipes(h)) {
			MachineDisplayData data = MachineDisplayData.of(holder);
			MachineDisplayData copy = codec.parse(ops, codec.encodeStart(ops, data).getOrThrow()).getOrThrow();
			h.assertValueEqual(copy.id(), holder.id().identifier(), "recipe id");
			assertSame(h, data.id() + "", holder.value(), copy.recipe());
		}
		h.succeed();
	}

	@GameTest
	public void unitSlotsRepeatEachCount(GameTestHelper h) {
		for (RecipeHolder<MachineRecipe> holder : recipes(h)) {
			MachineDisplayData data = MachineDisplayData.of(holder);
			List<CountedIngredient> ingredients = holder.value().ingredients();
			boolean mold = holder.value().mold().isPresent();
			int total = ingredients.stream().mapToInt(CountedIngredient::count).sum() + (mold ? 1 : 0);
			h.assertValueEqual(data.unitSlots().size(), total, data.id() + " slots");
			h.assertValueEqual(data.units().size(), total, data.id() + " units");
			for (int i = 0; i < ingredients.size(); i++) {
				h.assertValueEqual(Collections.frequency(data.unitSlots(), MachineBlockEntity.INPUT_0 + i),
						ingredients.get(i).count(), data.id() + " input " + i);
			}
			h.assertValueEqual(data.unitSlots().getLast() == MachineBlockEntity.MOLD, mold, data.id() + " mold last");
		}
		h.succeed();
	}

	@GameTest
	public void machineSlotsAcceptEveryRecipeUnit(GameTestHelper h) {
		Player player = h.makeMockPlayer(GameType.SURVIVAL);
		for (RecipeHolder<MachineRecipe> holder : recipes(h)) {
			MachineDisplayData data = MachineDisplayData.of(holder);
			h.setBlock(MachineMenuGameTest.POS, Blocks.AIR);
			MachineMenu menu = MachineMenuGameTest.menu(h, player,
					Block.byItem(holder.value().kind().machineItem()));
			List<Item> units = data.units().stream().map(RecipeViewerGameTest::firstItem).toList();
			for (int i = 0; i < units.size(); i++) {
				fill(h, menu.getSlot(data.unitSlots().get(i)), new ItemStack(units.get(i)), data.id() + " unit " + i);
			}
			List<Integer> counts = holder.value().ingredients().stream().map(CountedIngredient::count).toList();
			List<Integer> placed = new ArrayList<>();
			counts.forEach(count -> placed.add(menu.getSlot(placed.size()).getItem().getCount()));
			h.assertValueEqual(placed, counts, data.id() + " filled");
		}
		h.succeed();
	}

	/** Puts one item in a slot the way REI does, which loses the item when the slot refuses it. */
	private static void fill(GameTestHelper h, Slot slot, ItemStack one, String message) {
		h.assertTrue(slot.mayPlace(one), message + " accepted");
		if (slot.hasItem()) {
			slot.getItem().grow(1);
		} else {
			slot.set(one);
		}
	}

	private static List<RecipeHolder<MachineRecipe>> recipes(GameTestHelper h) {
		List<RecipeHolder<MachineRecipe>> found = new ArrayList<>();
		for (MachineKind kind : ModRecipes.kinds()) {
			found.addAll(h.getLevel().getServer().getRecipeManager().getAllOfType(kind.type()));
		}
		h.assertTrue(found.size() >= MIN_RECIPES, "all machine recipes checked, got " + found.size());
		return found;
	}

	private static void assertSame(GameTestHelper h, String id, MachineRecipe expected, MachineRecipe actual) {
		h.assertTrue(actual.kind() == expected.kind(), id + " machine");
		h.assertValueEqual(actual.ingredients().stream().map(CountedIngredient::count).toList(),
				expected.ingredients().stream().map(CountedIngredient::count).toList(), id + " counts");
		h.assertValueEqual(actual.ingredients().stream().map(i -> items(i.ingredient())).toList(),
				expected.ingredients().stream().map(i -> items(i.ingredient())).toList(), id + " ingredients");
		h.assertValueEqual(actual.mold().map(RecipeViewerGameTest::items),
				expected.mold().map(RecipeViewerGameTest::items), id + " mold");
		h.assertTrue(ItemStack.matches(actual.result().create(), expected.result().create()), id + " result");
		h.assertValueEqual(actual.processingTime(), expected.processingTime(), id + " time");
	}

	private static List<Item> items(Ingredient ingredient) {
		return ingredient.items().map(item -> item.value()).toList();
	}

	private static Item firstItem(Ingredient ingredient) {
		return items(ingredient).getFirst();
	}

	private static RegistryFriendlyByteBuf buffer(GameTestHelper h) {
		return new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
	}
}
