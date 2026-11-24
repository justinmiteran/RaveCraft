package com.ravecraft.mod.gametest;

import com.ravecraft.mod.blockentity.MachineBlockEntity;
import com.ravecraft.mod.compat.rei.MachineFill;
import com.ravecraft.mod.menu.MachineMenu;
import com.ravecraft.mod.recipe.CountedIngredient;
import com.ravecraft.mod.recipe.MachineKind;
import com.ravecraft.mod.recipe.MachineRecipe;
import com.ravecraft.mod.registry.ModItems;
import com.ravecraft.mod.registry.ModRecipes;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** The "+" button of REI, replayed step by step: it empties the machine's slots twice, then places the units. */
public class MachineFillGameTest {
	private static final int TIME = 100;

	@GameTest
	public void everyRecipeFillsAnEmptyMachine(GameTestHelper h) {
		for (RecipeHolder<MachineRecipe> holder : RecipeViewerGameTest.recipes(h)) {
			Player player = h.makeMockPlayer(GameType.SURVIVAL);
			MachineMenu menu = freshMenu(h, player, holder.value().kind());
			give(player, holder.value());
			fill(h, menu, player, holder.value());
			assertFilled(h, menu, holder);
		}
		h.succeed();
	}

	@GameTest
	public void everyRecipeFillsAFilledMachine(GameTestHelper h) {
		for (RecipeHolder<MachineRecipe> holder : RecipeViewerGameTest.recipes(h)) {
			Player player = h.makeMockPlayer(GameType.SURVIVAL);
			MachineMenu menu = freshMenu(h, player, holder.value().kind());
			give(player, holder.value());
			fill(h, menu, player, holder.value());
			give(player, holder.value());
			fill(h, menu, player, holder.value());
			assertFilled(h, menu, holder);
		}
		h.succeed();
	}

	@GameTest
	public void theInstalledMoldStays(GameTestHelper h) {
		MachineRecipe recipe = new MachineRecipe(ModRecipes.EXTRUDING, true, List.of(one(Items.IRON_INGOT)),
				Optional.of(Ingredient.of(ModItems.SHEET_MOLD)), new ItemStackTemplate(Items.STICK), TIME);
		MachineFill installed = MachineFill.of(recipe, new ItemStack(ModItems.SHEET_MOLD), List.of());
		h.assertFalse(installed.slots().contains(MachineBlockEntity.MOLD), "mold slot left alone");
		h.assertValueEqual(installed.units().size(), 1, "no mold asked");
		MachineFill other = MachineFill.of(recipe, new ItemStack(ModItems.BOARD_MOLD), List.of());
		h.assertTrue(other.slots().contains(MachineBlockEntity.MOLD), "another mold is replaced");
		h.assertValueEqual(other.units().getLast().pageSlot(), 1, "mold shown after the ingredient");
		h.succeed();
	}

	@GameTest
	public void oneIngredientNeverMixesItems(GameTestHelper h) {
		Ingredient nuggets = Ingredient.of(Items.GOLD_NUGGET, Items.IRON_NUGGET);
		MachineRecipe recipe = etching(new CountedIngredient(nuggets, 2));
		ItemStack gold = new ItemStack(Items.GOLD_NUGGET);
		assertChoices(h, recipe, List.of(gold, new ItemStack(Items.IRON_NUGGET, 2)),
				List.of(Items.IRON_NUGGET, Items.IRON_NUGGET));
		assertChoices(h, recipe, List.of(gold, new ItemStack(Items.IRON_NUGGET)),
				List.of(Items.GOLD_NUGGET, Items.GOLD_NUGGET));
		assertChoices(h, recipe, List.of(), List.of(Items.GOLD_NUGGET, Items.GOLD_NUGGET));
		h.succeed();
	}

	@GameTest
	public void twoIngredientsNeverShareAnItem(GameTestHelper h) {
		Ingredient nuggets = Ingredient.of(Items.GOLD_NUGGET, Items.IRON_NUGGET);
		MachineRecipe recipe = etching(one(Items.GOLD_NUGGET), new CountedIngredient(nuggets, 1));
		assertChoices(h, recipe, List.of(new ItemStack(Items.GOLD_NUGGET, 9), new ItemStack(Items.IRON_NUGGET)),
				List.of(Items.GOLD_NUGGET, Items.IRON_NUGGET));
		h.succeed();
	}

	@GameTest
	public void inputsTheRecipeDoesNotUseAreEmptied(GameTestHelper h) {
		Player player = h.makeMockPlayer(GameType.SURVIVAL);
		MachineMenu menu = freshMenu(h, player, ModRecipes.ETCHING);
		menu.inputSlots().getLast().set(new ItemStack(Items.IRON_INGOT, 3));
		player.getInventory().add(new ItemStack(Items.IRON_INGOT));
		fill(h, menu, player, etching(one(Items.IRON_INGOT)));
		h.assertValueEqual(menu.inputSlots().getFirst().getItem().getCount(), 1, "one ingot placed");
		h.assertTrue(menu.inputSlots().getLast().getItem().isEmpty(), "other input emptied");
		h.assertValueEqual(player.getInventory().countItem(Items.IRON_INGOT), 3, "nothing lost");
		h.succeed();
	}

	private static void assertChoices(GameTestHelper h, MachineRecipe recipe, List<ItemStack> inventory,
			List<Item> expected) {
		List<Item> chosen = MachineFill.of(recipe, ItemStack.EMPTY, inventory).units().stream()
				.map(unit -> unit.choices().size() == 1 ? unit.choices().getFirst().getItem() : Items.AIR).toList();
		h.assertValueEqual(chosen, expected, "chosen items");
	}

	private static void assertFilled(GameTestHelper h, MachineMenu menu, RecipeHolder<MachineRecipe> holder) {
		List<CountedIngredient> ingredients = holder.value().ingredients();
		for (int i = 0; i < ingredients.size(); i++) {
			ItemStack stack = menu.getSlot(MachineBlockEntity.INPUT_0 + i).getItem();
			h.assertTrue(ingredients.get(i).ingredient().test(stack), holder.id() + " input " + i);
			h.assertValueEqual(stack.getCount(), ingredients.get(i).count(), holder.id() + " count " + i);
		}
		h.assertTrue(holder.value().mold().map(mold -> mold.test(menu.moldSlot().getItem())).orElse(true),
				holder.id() + " mold");
	}

	/** What REI does on the server, in its order. */
	private static void fill(GameTestHelper h, MachineMenu menu, Player player, MachineRecipe recipe) {
		Inventory inventory = player.getInventory();
		MachineFill fill = MachineFill.of(recipe, menu.moldSlot().getItem(), inventory.getNonEquipmentItems());
		List<Slot> slots = fill.slots().stream().map(menu::getSlot).toList();
		clean(h, slots, player);
		clean(h, slots, player);
		for (int i = 0; i < fill.units().size(); i++) {
			place(h, slots.get(i), inventory, fill.units().get(i));
		}
	}

	/** REI refuses the whole transfer when one slot cannot be modified, even an empty one. */
	private static void clean(GameTestHelper h, List<Slot> slots, Player player) {
		for (Slot slot : slots) {
			h.assertTrue(slot.allowModification(player), "slot " + slot.index + " modifiable");
			player.getInventory().add(slot.remove(slot.getItem().getCount()));
		}
	}

	/** REI takes the item before it asks the slot, so a refused item would be lost. */
	private static void place(GameTestHelper h, Slot slot, Inventory inventory, MachineFill.Unit unit) {
		Item item = unit.choices().stream().map(ItemStack::getItem)
				.filter(choice -> inventory.countItem(choice) > 0).findFirst()
				.orElseThrow(() -> h.assertionException("item missing for slot " + slot.index));
		ItemStack one = new ItemStack(item);
		inventory.removeItem(inventory.findSlotMatchingItem(one), 1);
		h.assertTrue(slot.mayPlace(one), item + " accepted by slot " + slot.index);
		if (slot.hasItem()) {
			slot.getItem().grow(1);
		} else {
			slot.set(one);
		}
	}

	private static void give(Player player, MachineRecipe recipe) {
		for (CountedIngredient counted : recipe.ingredients()) {
			player.getInventory().add(new ItemStack(firstItem(counted.ingredient()), counted.count()));
		}
		recipe.mold().ifPresent(mold -> player.getInventory().add(new ItemStack(firstItem(mold))));
	}

	private static Item firstItem(Ingredient ingredient) {
		return ingredient.items().findFirst().orElseThrow().value();
	}

	private static CountedIngredient one(Item item) {
		return new CountedIngredient(Ingredient.of(item), 1);
	}

	private static MachineRecipe etching(CountedIngredient... ingredients) {
		return new MachineRecipe(ModRecipes.ETCHING, true, List.of(ingredients), Optional.empty(),
				new ItemStackTemplate(Items.STICK), TIME);
	}

	private static MachineMenu freshMenu(GameTestHelper h, Player player, MachineKind kind) {
		h.setBlock(MachineMenuGameTest.POS, Blocks.AIR);
		return MachineMenuGameTest.menu(h, player, Block.byItem(kind.machineItem()));
	}
}
