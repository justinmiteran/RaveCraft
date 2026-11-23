package com.ravecraft.mod.gametest;

import com.ravecraft.mod.block.MachineBlock;
import com.ravecraft.mod.blockentity.MachineBlockEntity;
import com.ravecraft.mod.registry.ModBlocks;
import com.ravecraft.mod.registry.ModItems;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;

public class CoilWinderGameTest {
	private static final BlockPos POS = new BlockPos(1, 2, 1);

	@GameTest(maxTicks = 200)
	public void winderMakesWire(GameTestHelper h) {
		MachineBlockEntity winder = winder(h, new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.REDSTONE));
		h.runAfterDelay(90, () -> h.assertTrue(winder.getItem(MachineBlockEntity.OUTPUT).isEmpty(), "not yet"));
		h.runAfterDelay(110, () -> {
			h.assertTrue(winder.getItem(MachineBlockEntity.OUTPUT).is(ModItems.COPPER_WIRE), "wire made");
			h.assertValueEqual(winder.getItem(MachineBlockEntity.OUTPUT).getCount(), 4, "four wires");
			h.assertTrue(winder.getItem(MachineBlockEntity.INPUT_0).isEmpty(), "ingot used");
			h.assertTrue(winder.getItem(MachineBlockEntity.ENERGY).isEmpty(), "dust used");
			h.assertValueEqual(winder.energy(), 7, "reserve");
			h.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void winderMakesCoil(GameTestHelper h) {
		MachineBlockEntity winder = winder(h, new ItemStack(ModItems.COPPER_WIRE, 4), new ItemStack(Items.REDSTONE));
		h.runAfterDelay(110, () -> {
			h.assertTrue(winder.getItem(MachineBlockEntity.OUTPUT).is(ModItems.VOICE_COIL), "coil made");
			h.assertValueEqual(winder.getItem(MachineBlockEntity.OUTPUT).getCount(), 1, "one coil");
			h.assertTrue(winder.getItem(MachineBlockEntity.INPUT_0).isEmpty(), "wires used");
			h.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void outputFullBlocks(GameTestHelper h) {
		MachineBlockEntity winder = winder(h, new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.REDSTONE));
		winder.setItem(MachineBlockEntity.OUTPUT, new ItemStack(ModItems.COPPER_WIRE, 64));
		h.runAfterDelay(130, () -> {
			h.assertValueEqual(winder.getItem(MachineBlockEntity.INPUT_0).getCount(), 1, "ingot kept");
			h.assertValueEqual(winder.getItem(MachineBlockEntity.OUTPUT).getCount(), 64, "output unchanged");
			h.assertValueEqual(winder.progress(), winder.total(), "waits at the end");
			h.succeed();
		});
	}

	@GameTest(maxTicks = 300)
	public void noEnergyWaits(GameTestHelper h) {
		MachineBlockEntity winder = winder(h, new ItemStack(Items.COPPER_INGOT), ItemStack.EMPTY);
		h.runAfterDelay(110, () -> {
			h.assertTrue(winder.getItem(MachineBlockEntity.OUTPUT).isEmpty(), "nothing without energy");
			h.assertValueEqual(winder.progress(), 0, "no progress");
			winder.setItem(MachineBlockEntity.ENERGY, new ItemStack(Items.REDSTONE_BLOCK));
		});
		h.runAfterDelay(230, () -> {
			h.assertTrue(winder.getItem(MachineBlockEntity.OUTPUT).is(ModItems.COPPER_WIRE), "runs once powered");
			h.assertValueEqual(winder.energy(), 71, "block gives 72");
			h.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void sameInputKeepsProgress(GameTestHelper h) {
		MachineBlockEntity winder = winder(h, new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.REDSTONE));
		h.runAfterDelay(50, () -> winder.setItem(MachineBlockEntity.INPUT_0, new ItemStack(Items.COPPER_INGOT, 2)));
		h.runAfterDelay(110, () -> {
			h.assertValueEqual(winder.getItem(MachineBlockEntity.OUTPUT).getCount(), 4, "first operation done");
			h.assertValueEqual(winder.getItem(MachineBlockEntity.INPUT_0).getCount(), 1, "one ingot left");
			h.succeed();
		});
	}

	@GameTest(maxTicks = 300)
	public void changedInputResets(GameTestHelper h) {
		MachineBlockEntity winder = winder(h, new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.REDSTONE));
		h.runAfterDelay(50, () -> winder.setItem(MachineBlockEntity.INPUT_0, new ItemStack(ModItems.COPPER_WIRE, 4)));
		h.runAfterDelay(140, () -> h.assertTrue(winder.getItem(MachineBlockEntity.OUTPUT).isEmpty(), "restarted"));
		h.runAfterDelay(160, () -> {
			h.assertTrue(winder.getItem(MachineBlockEntity.OUTPUT).is(ModItems.VOICE_COIL), "coil after a full run");
			h.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void hopperTopFillsInputs(GameTestHelper h) {
		MachineBlockEntity winder = winder(h, ItemStack.EMPTY, ItemStack.EMPTY);
		hopper(h, POS.above(), Direction.DOWN, new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.REDSTONE));
		h.runAfterDelay(40, () -> {
			h.assertTrue(winder.getItem(MachineBlockEntity.INPUT_0).is(Items.COPPER_INGOT), "ingot in slot 0");
			h.assertTrue(winder.getItem(MachineBlockEntity.INPUT_1).is(Items.REDSTONE), "top fills inputs only");
			h.assertTrue(winder.getItem(MachineBlockEntity.ENERGY).isEmpty(), "no energy from the top");
			h.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void hopperSideOnlyEnergy(GameTestHelper h) {
		MachineBlockEntity winder = winder(h, ItemStack.EMPTY, ItemStack.EMPTY);
		HopperBlockEntity hopper = hopper(h, POS.west(), Direction.EAST, new ItemStack(Items.COBBLESTONE),
				new ItemStack(Items.REDSTONE));
		h.runAfterDelay(40, () -> {
			h.assertTrue(winder.getItem(MachineBlockEntity.ENERGY).is(Items.REDSTONE), "dust in the energy slot");
			h.assertTrue(winder.getItem(MachineBlockEntity.INPUT_0).isEmpty(), "no input from the side");
			h.assertTrue(hopper.getItem(0).is(Items.COBBLESTONE), "cobblestone stays");
			h.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void hopperBottomExtractsOutput(GameTestHelper h) {
		MachineBlockEntity winder = winder(h, new ItemStack(Items.COPPER_INGOT), ItemStack.EMPTY);
		winder.setItem(MachineBlockEntity.OUTPUT, new ItemStack(ModItems.COPPER_WIRE, 4));
		HopperBlockEntity hopper = hopper(h, POS.below(), Direction.DOWN);
		h.runAfterDelay(60, () -> {
			h.assertValueEqual(count(hopper, ModItems.COPPER_WIRE), 4, "wire pulled");
			h.assertValueEqual(count(hopper, Items.COPPER_INGOT), 0, "input not pulled");
			h.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void breakingDropsContents(GameTestHelper h) {
		MachineBlockEntity winder = winder(h, new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.REDSTONE, 2));
		winder.setItem(MachineBlockEntity.OUTPUT, new ItemStack(ModItems.COPPER_WIRE, 3));
		h.runAfterDelay(50, () -> {
			h.getLevel().destroyBlock(h.absolutePos(POS), true);
			List<ItemEntity> drops = h.getEntities(EntityTypes.ITEM, POS, 2);
			h.assertValueEqual(dropped(drops, Items.COPPER_INGOT), 1, "ingot dropped");
			h.assertValueEqual(dropped(drops, Items.REDSTONE), 1, "dust dropped");
			h.assertValueEqual(dropped(drops, ModItems.COPPER_WIRE), 3, "wire dropped");
			h.assertValueEqual(dropped(drops, ModBlocks.COIL_WINDER.asItem()), 1, "the winder itself");
			h.assertValueEqual(drops.size(), 4, "one drop each");
			h.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void saveRoundTrip(GameTestHelper h) {
		MachineBlockEntity winder = winder(h, new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.REDSTONE));
		h.runAfterDelay(50, () -> {
			CompoundTag tag = winder.saveWithoutMetadata(h.getLevel().registryAccess());
			MachineBlockEntity copy = new MachineBlockEntity(winder.getBlockPos(), winder.getBlockState());
			copy.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(),
					tag));
			h.assertValueEqual(copy.progress(), winder.progress(), "progress");
			h.assertValueEqual(copy.energy(), winder.energy(), "energy");
			h.assertTrue(copy.getItem(MachineBlockEntity.INPUT_0).is(Items.COPPER_INGOT), "items");
			h.succeed();
		});
	}

	@GameTest(maxTicks = 300)
	public void litDuringOperation(GameTestHelper h) {
		MachineBlockEntity winder = winder(h, ItemStack.EMPTY, new ItemStack(Items.REDSTONE));
		h.runAfterDelay(10, () -> {
			h.assertBlockProperty(POS, MachineBlock.LIT, false);
			winder.setItem(MachineBlockEntity.INPUT_0, new ItemStack(Items.COPPER_INGOT));
		});
		h.runAfterDelay(40, () -> h.assertBlockProperty(POS, MachineBlock.LIT, true));
		h.runAfterDelay(140, () -> {
			h.assertBlockProperty(POS, MachineBlock.LIT, false);
			h.succeed();
		});
	}

	@GameTest
	public void pickaxeTier(GameTestHelper h) {
		var state = ModBlocks.COIL_WINDER.defaultBlockState();
		h.assertFalse(ItemStack.EMPTY.isCorrectToolForDrops(state), "bare hand");
		h.assertFalse(new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(state), "wooden pickaxe");
		h.assertTrue(new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(state), "stone pickaxe");
		h.succeed();
	}

	private static MachineBlockEntity winder(GameTestHelper h, ItemStack input, ItemStack energy) {
		h.setBlock(POS, ModBlocks.COIL_WINDER);
		MachineBlockEntity winder = h.getBlockEntity(POS, MachineBlockEntity.class);
		winder.setItem(MachineBlockEntity.INPUT_0, input);
		winder.setItem(MachineBlockEntity.ENERGY, energy);
		return winder;
	}

	private static HopperBlockEntity hopper(GameTestHelper h, BlockPos pos, Direction facing, ItemStack... items) {
		h.setBlock(pos, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, facing));
		HopperBlockEntity hopper = h.getBlockEntity(pos, HopperBlockEntity.class);
		for (int i = 0; i < items.length; i++) {
			hopper.setItem(i, items[i]);
		}
		return hopper;
	}

	private static int count(HopperBlockEntity hopper, Item item) {
		int total = 0;
		for (int i = 0; i < hopper.getContainerSize(); i++) {
			total += hopper.getItem(i).is(item) ? hopper.getItem(i).getCount() : 0;
		}
		return total;
	}

	private static int dropped(List<ItemEntity> drops, Item item) {
		return drops.stream().filter(e -> e.getItem().is(item)).mapToInt(e -> e.getItem().getCount()).sum();
	}
}
