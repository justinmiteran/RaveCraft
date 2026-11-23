package com.ravecraft.mod.gametest;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.blockentity.MachineBlockEntity;
import com.ravecraft.mod.menu.MachineMenu;
import com.ravecraft.mod.recipe.CountedIngredient;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.ravecraft.mod.registry.ModBlocks;
import com.ravecraft.mod.registry.ModItems;
import com.ravecraft.mod.registry.ModRecipes;
import net.minecraft.core.Direction;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.RecipeBookMenu.PostPlaceAction;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;

public class MachineMenuGameTest {
	private static final BlockPos POS = new BlockPos(1, 2, 1);
	/** Menu index of the player's first main inventory slot (inventory slot 9). */
	private static final int FIRST_INVENTORY = MachineBlockEntity.SIZE;

	@GameTest
	public void quickMoveStackRoutes(GameTestHelper h) {
		Player player = h.makeMockPlayer(GameType.SURVIVAL);
		MachineMenu menu = menu(h, player);
		Inventory inventory = player.getInventory();
		inventory.setItem(9, new ItemStack(Items.REDSTONE, 5));
		inventory.setItem(10, new ItemStack(Items.COPPER_INGOT, 3));
		menu.getSlot(MachineBlockEntity.OUTPUT).set(new ItemStack(ModItems.COPPER_WIRE, 4));
		menu.quickMoveStack(player, FIRST_INVENTORY);
		menu.quickMoveStack(player, FIRST_INVENTORY + 1);
		menu.quickMoveStack(player, MachineBlockEntity.OUTPUT);
		h.assertValueEqual(menu.getSlot(MachineBlockEntity.ENERGY).getItem().getCount(), 5, "dust to energy");
		h.assertTrue(menu.getSlot(MachineBlockEntity.INPUT_0).getItem().is(Items.COPPER_INGOT), "ingot to input");
		h.assertValueEqual(menu.getSlot(MachineBlockEntity.INPUT_0).getItem().getCount(), 3, "all three");
		h.assertTrue(menu.getSlot(MachineBlockEntity.OUTPUT).getItem().isEmpty(), "output emptied");
		h.assertValueEqual(count(inventory, ModItems.COPPER_WIRE), 4, "wire to the player");
		h.succeed();
	}

	@GameTest
	public void shiftClickRedstoneNeverReachesInputs(GameTestHelper h) {
		Player player = h.makeMockPlayer(GameType.SURVIVAL);
		MachineMenu menu = menu(h, player);
		menu.getSlot(MachineBlockEntity.ENERGY).set(new ItemStack(Items.REDSTONE, 60));
		player.getInventory().setItem(9, new ItemStack(Items.REDSTONE, 64));
		menu.clicked(FIRST_INVENTORY, 0, ContainerInput.QUICK_MOVE, player);
		h.assertValueEqual(menu.getSlot(MachineBlockEntity.ENERGY).getItem().getCount(), 64, "energy filled");
		for (int i = MachineBlockEntity.INPUT_0; i <= MachineBlockEntity.INPUT_2; i++) {
			h.assertTrue(menu.getSlot(i).getItem().isEmpty(), "no redstone in input " + i);
		}
		h.assertValueEqual(count(player.getInventory(), Items.REDSTONE), 60, "rest stays with the player");
		h.succeed();
	}

	@GameTest
	public void ingredientCountIsBounded(GameTestHelper h) {
		var ops = h.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
		var json = JsonParser.parseString("{\"ingredient\":\"minecraft:copper_ingot\",\"count\":1000}");
		h.assertTrue(CountedIngredient.CODEC.parse(ops, json).isError(), "count above a stack refused");
		var ok = JsonParser.parseString("{\"ingredient\":\"minecraft:copper_ingot\",\"count\":64}");
		h.assertTrue(CountedIngredient.CODEC.parse(ops, ok).isSuccess(), "a full stack accepted");
		h.succeed();
	}

	@GameTest
	public void placementFillsInputs(GameTestHelper h) {
		Player player = h.makeMockPlayer(GameType.SURVIVAL);
		player.getInventory().setItem(9, new ItemStack(ModItems.COPPER_WIRE, 8));
		MachineMenu menu = menu(h, player);
		PostPlaceAction action = place(h, menu, player, "voice_coil_from_winding", false);
		h.assertValueEqual(action, PostPlaceAction.NOTHING, "placed");
		h.assertValueEqual(menu.getSlot(MachineBlockEntity.INPUT_0).getItem().getCount(), 4, "one craft");
		h.assertValueEqual(count(player.getInventory(), ModItems.COPPER_WIRE), 4, "four left");
		place(h, menu, player, "voice_coil_from_winding", true);
		h.assertValueEqual(menu.getSlot(MachineBlockEntity.INPUT_0).getItem().getCount(), 8, "max crafts");
		h.assertValueEqual(count(player.getInventory(), ModItems.COPPER_WIRE), 0, "all taken");
		h.succeed();
	}

	@GameTest
	public void placementReturnsOldInputs(GameTestHelper h) {
		Player player = h.makeMockPlayer(GameType.SURVIVAL);
		player.getInventory().setItem(9, new ItemStack(Items.COPPER_INGOT, 2));
		MachineMenu menu = menu(h, player);
		menu.getSlot(MachineBlockEntity.INPUT_0).set(new ItemStack(Items.COBBLESTONE, 5));
		place(h, menu, player, "copper_wire_from_winding", false);
		h.assertValueEqual(count(player.getInventory(), Items.COBBLESTONE), 5, "back in the inventory");
		h.assertValueEqual(menu.getSlot(MachineBlockEntity.INPUT_0).getItem().getCount(), 1, "ingot placed");
		h.succeed();
	}

	@GameTest
	public void placementGhostWhenMissing(GameTestHelper h) {
		Player player = h.makeMockPlayer(GameType.SURVIVAL);
		player.getInventory().setItem(9, new ItemStack(ModItems.COPPER_WIRE, 3));
		MachineMenu menu = menu(h, player);
		PostPlaceAction action = place(h, menu, player, "voice_coil_from_winding", false);
		h.assertValueEqual(action, PostPlaceAction.PLACE_GHOST_RECIPE, "ghost");
		h.assertValueEqual(count(player.getInventory(), ModItems.COPPER_WIRE), 3, "inventory unchanged");
		h.assertTrue(menu.getSlot(MachineBlockEntity.INPUT_0).getItem().isEmpty(), "nothing placed");
		h.succeed();
	}

	@GameTest
	public void winderHasOneInput(GameTestHelper h) {
		MachineMenu menu = menu(h, h.makeMockPlayer(GameType.SURVIVAL));
		MachineBlockEntity winder = h.getBlockEntity(POS, MachineBlockEntity.class);
		h.assertTrue(menu.getSlot(MachineBlockEntity.INPUT_0).isActive(), "first input shown");
		h.assertFalse(menu.getSlot(MachineBlockEntity.INPUT_1).isActive(), "second input hidden");
		h.assertFalse(menu.getSlot(MachineBlockEntity.INPUT_2).isActive(), "third input hidden");
		h.assertFalse(winder.canPlaceItem(MachineBlockEntity.INPUT_1, new ItemStack(Items.COPPER_INGOT)), "unused input");
		h.assertValueEqual(winder.getSlotsForFace(Direction.UP).length, 1, "hoppers fill one input");
		h.assertValueEqual(menu.inputSlots().size(), 1, "the book places in one input");
		h.succeed();
	}

	@GameTest
	public void windingRecipeTakesOneIngredient(GameTestHelper h) {
		var ops = h.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
		String result = ",\"result\":{\"id\":\"ravecraft:copper_wire\"}}";
		String one = "{\"ingredients\":[{\"ingredient\":\"minecraft:copper_ingot\"}]" + result;
		String two = "{\"ingredients\":[{\"ingredient\":\"minecraft:copper_ingot\"},"
				+ "{\"ingredient\":\"minecraft:iron_ingot\"}]" + result;
		var codec = ModRecipes.WINDING.serializer().codec().codec();
		h.assertTrue(codec.parse(ops, JsonParser.parseString(one)).isSuccess(), "one ingredient accepted");
		h.assertTrue(codec.parse(ops, JsonParser.parseString(two)).isError(), "two ingredients refused");
		h.succeed();
	}

	@GameTest
	public void bookTypeIsFurnace(GameTestHelper h) {
		MachineMenu menu = menu(h, h.makeMockPlayer(GameType.SURVIVAL));
		h.assertValueEqual(menu.getRecipeBookType(), RecipeBookType.FURNACE, "furnace book");
		h.succeed();
	}

	private static MachineMenu menu(GameTestHelper h, Player player) {
		h.setBlock(POS, ModBlocks.COIL_WINDER);
		return new MachineMenu(0, player.getInventory(), h.getBlockEntity(POS, MachineBlockEntity.class));
	}

	private static PostPlaceAction place(GameTestHelper h, MachineMenu menu, Player player, String name,
			boolean useMaxItems) {
		RecipeHolder<?> recipe = h.getLevel().getServer().getRecipeManager()
				.byKey(ResourceKey.create(Registries.RECIPE, RaveCraft.id(name)))
				.orElseThrow(() -> h.assertionException(name + " missing"));
		return menu.handlePlacement(useMaxItems, false, recipe, h.getLevel(), player.getInventory());
	}

	private static int count(Inventory inventory, Item item) {
		int total = 0;
		for (ItemStack stack : inventory.getNonEquipmentItems()) {
			total += stack.is(item) ? stack.getCount() : 0;
		}
		return total;
	}
}
