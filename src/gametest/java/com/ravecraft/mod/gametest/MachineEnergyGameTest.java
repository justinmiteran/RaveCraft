package com.ravecraft.mod.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.ravecraft.mod.blockentity.MachineBlockEntity;
import com.ravecraft.mod.core.machine.MachineConstants;
import com.ravecraft.mod.recipe.MachineEnergy;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.impl.recipe.sync.RecipeSyncImpl;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class MachineEnergyGameTest {
	@GameTest
	public void redstoneGivesItsOperations(GameTestHelper h) {
		h.assertValueEqual(MachineEnergy.operations(h.getLevel(), new ItemStack(Items.REDSTONE)),
				MachineConstants.OPERATIONS_PER_DUST, "dust");
		h.assertValueEqual(MachineEnergy.operations(h.getLevel(), new ItemStack(Items.REDSTONE_BLOCK)),
				MachineConstants.OPERATIONS_PER_BLOCK, "block");
		h.assertValueEqual(MachineEnergy.operations(h.getLevel(), new ItemStack(Items.DIRT)), 0, "dirt");
		h.assertValueEqual(MachineEnergy.operations(h.getLevel(), ItemStack.EMPTY), 0, "nothing");
		h.assertTrue(MachineBlockEntity.isEnergy(h.getLevel(), new ItemStack(Items.REDSTONE)), "dust is energy");
		h.assertFalse(MachineBlockEntity.isEnergy(h.getLevel(), new ItemStack(Items.COAL)), "coal is not");
		h.succeed();
	}

	@GameTest
	public void energyRecipesSyncToClients(GameTestHelper h) {
		h.assertTrue(RecipeSyncImpl.isSynced(MachineEnergy.SERIALIZER), "energy recipes reach the client");
		h.assertValueEqual(h.getLevel().recipeAccess().getSynchronizedRecipes().getAllOfType(MachineEnergy.TYPE)
				.size(), 2, "dust and block");
		h.succeed();
	}

	@GameTest
	public void operationsOutsideTheReserveAreRejected(GameTestHelper h) {
		h.assertTrue(parses(h, 1), "one operation");
		h.assertTrue(parses(h, MachineConstants.MAX_ENERGY), "a full reserve");
		h.assertFalse(parses(h, 0), "no operation");
		h.assertFalse(parses(h, MachineConstants.MAX_ENERGY + 1), "more than the reserve");
		h.succeed();
	}

	private static boolean parses(GameTestHelper h, int operations) {
		String json = "{\"ingredient\":\"minecraft:redstone\",\"operations\":" + operations + "}";
		return MachineEnergy.SERIALIZER.codec().codec()
				.parse(RegistryOps.create(JsonOps.INSTANCE, h.getLevel().registryAccess()), JsonParser.parseString(json))
				.isSuccess();
	}
}
