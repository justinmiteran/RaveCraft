package com.ravecraft.mod.gametest;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.blockentity.MachineBlockEntity;
import com.ravecraft.mod.registry.ModBlocks;
import com.ravecraft.mod.registry.ModItems;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

/** The magnetizer, etcher and extruder at work, and the extruder's mold slot (spec 5, 6.1). */
public class MachinesGameTest {
	private static final BlockPos POS = new BlockPos(1, 2, 1);

	/** One machine recipe: the machine, its mold (or null), the result, then the input stacks in slot order. */
	private record Case(Block machine, Item mold, Item result, int count, ItemStack... inputs) {
	}

	private static List<Case> cases() {
		return List.of(
				new Case(ModBlocks.MAGNETIZER, null, ModItems.IRON_MAGNET, 1, new ItemStack(Items.IRON_INGOT),
						new ItemStack(Items.REDSTONE)),
				new Case(ModBlocks.MAGNETIZER, null, ModItems.NEODYMIUM_MAGNET, 2,
						new ItemStack(ModItems.NEODYMIUM_INGOT), new ItemStack(Items.IRON_INGOT)),
				new Case(ModBlocks.ETCHER, null, ModItems.SIMPLE_CIRCUIT, 1, new ItemStack(ModItems.CIRCUIT_BOARD),
						new ItemStack(ModItems.COPPER_WIRE, 2), new ItemStack(Items.REDSTONE)),
				new Case(ModBlocks.ETCHER, null, ModItems.CHIP, 1, new ItemStack(ModItems.SILICON_WAFER),
						new ItemStack(Items.GOLD_NUGGET, 2), new ItemStack(Items.REDSTONE)),
				new Case(ModBlocks.EXTRUDER, ModItems.SHEET_MOLD, ModItems.PLASTIC_SHEET, 2,
						new ItemStack(ModItems.BITUMEN)),
				new Case(ModBlocks.EXTRUDER, ModItems.CASING_MOLD, ModItems.PLASTIC_CASING, 1,
						new ItemStack(ModItems.BITUMEN, 3)),
				new Case(ModBlocks.EXTRUDER, ModItems.MEMBRANE_MOLD, ModItems.REINFORCED_MEMBRANE, 1,
						new ItemStack(ModItems.BITUMEN, 2)),
				new Case(ModBlocks.EXTRUDER, ModItems.BOARD_MOLD, ModItems.CIRCUIT_BOARD, 2,
						new ItemStack(ModItems.BITUMEN)));
	}

	@GameTest(maxTicks = 200)
	public void everyMachineRecipeRuns(GameTestHelper h) {
		List<Case> cases = cases();
		MachineBlockEntity[] machines = new MachineBlockEntity[cases.size()];
		for (int i = 0; i < cases.size(); i++) {
			Case c = cases.get(i);
			machines[i] = machine(h, new BlockPos(1 + 2 * (i % 4), 2, 1 + 2 * (i / 4)), c.machine(), c.mold(),
					new ItemStack(Items.REDSTONE), c.inputs());
		}
		h.runAfterDelay(110, () -> {
			for (int i = 0; i < cases.size(); i++) {
				Case c = cases.get(i);
				MachineBlockEntity m = machines[i];
				String name = c.result().toString();
				h.assertTrue(m.getItem(MachineBlockEntity.OUTPUT).is(c.result()), name + " made");
				h.assertValueEqual(m.getItem(MachineBlockEntity.OUTPUT).getCount(), c.count(), name + " count");
				for (int slot = MachineBlockEntity.INPUT_0; slot <= MachineBlockEntity.INPUT_2; slot++) {
					h.assertTrue(m.getItem(slot).isEmpty(), name + " input " + slot + " used");
				}
				h.assertTrue(c.mold() == null || m.getItem(MachineBlockEntity.MOLD).is(c.mold()), name + " mold kept");
				h.assertValueEqual(m.energy(), 7, name + " reserve");
			}
			h.succeed();
		});
	}

	@GameTest(maxTicks = 300)
	public void extruderWithoutMoldIdles(GameTestHelper h) {
		MachineBlockEntity extruder = machine(h, POS, ModBlocks.EXTRUDER, null, new ItemStack(Items.REDSTONE),
				new ItemStack(ModItems.BITUMEN));
		h.runAfterDelay(200, () -> {
			h.assertTrue(extruder.getItem(MachineBlockEntity.OUTPUT).isEmpty(), "nothing without a mold");
			h.assertValueEqual(extruder.getItem(MachineBlockEntity.ENERGY).getCount(), 1, "dust kept");
			h.assertValueEqual(extruder.energy(), 0, "no energy spent");
			h.succeed();
		});
	}

	@GameTest(maxTicks = 300)
	public void changingMoldResets(GameTestHelper h) {
		MachineBlockEntity extruder = machine(h, POS, ModBlocks.EXTRUDER, ModItems.SHEET_MOLD,
				new ItemStack(Items.REDSTONE), new ItemStack(ModItems.BITUMEN));
		h.runAfterDelay(50, () -> extruder.setItem(MachineBlockEntity.MOLD, new ItemStack(ModItems.BOARD_MOLD)));
		h.runAfterDelay(140, () -> h.assertTrue(extruder.getItem(MachineBlockEntity.OUTPUT).isEmpty(), "restarted"));
		h.runAfterDelay(160, () -> {
			h.assertTrue(extruder.getItem(MachineBlockEntity.OUTPUT).is(ModItems.CIRCUIT_BOARD), "board mold result");
			h.assertValueEqual(extruder.getItem(MachineBlockEntity.OUTPUT).getCount(), 2, "two boards");
			h.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void hoppersIgnoreMold(GameTestHelper h) {
		MachineBlockEntity extruder = machine(h, POS, ModBlocks.EXTRUDER, ModItems.BOARD_MOLD, ItemStack.EMPTY);
		extruder.setItem(MachineBlockEntity.OUTPUT, new ItemStack(ModItems.PLASTIC_SHEET, 2));
		HopperBlockEntity below = hopper(h, POS.below(), Direction.DOWN);
		HopperBlockEntity side = hopper(h, POS.west(), Direction.EAST, new ItemStack(ModItems.SHEET_MOLD));
		h.runAfterDelay(60, () -> {
			h.assertValueEqual(count(below, ModItems.PLASTIC_SHEET), 2, "output pulled");
			h.assertValueEqual(count(below, ModItems.BOARD_MOLD), 0, "mold not pulled");
			h.assertTrue(extruder.getItem(MachineBlockEntity.MOLD).is(ModItems.BOARD_MOLD), "mold stays");
			h.assertValueEqual(count(side, ModItems.SHEET_MOLD), 1, "side hopper keeps its mold");
			for (Direction dir : Direction.values()) {
				h.assertFalse(Arrays.stream(extruder.getSlotsForFace(dir)).anyMatch(s -> s == MachineBlockEntity.MOLD),
						"mold slot hidden from " + dir);
			}
			h.succeed();
		});
	}

	@GameTest
	public void moldSlotRefusesOthers(GameTestHelper h) {
		MachineBlockEntity extruder = machine(h, POS, ModBlocks.EXTRUDER, null, ItemStack.EMPTY);
		h.assertFalse(extruder.canPlaceItem(MachineBlockEntity.MOLD, new ItemStack(ModItems.BITUMEN)), "no bitumen");
		h.assertTrue(extruder.canPlaceItem(MachineBlockEntity.MOLD, new ItemStack(ModItems.SHEET_MOLD)), "a mold");
		MachineBlockEntity magnetizer = machine(h, POS.east(2), ModBlocks.MAGNETIZER, null, ItemStack.EMPTY);
		h.assertFalse(magnetizer.canPlaceItem(MachineBlockEntity.MOLD, new ItemStack(ModItems.SHEET_MOLD)),
				"no mold on the magnetizer");
		h.succeed();
	}

	@GameTest(maxTicks = 100)
	public void breakingDropsMold(GameTestHelper h) {
		machine(h, POS, ModBlocks.EXTRUDER, ModItems.CASING_MOLD, ItemStack.EMPTY);
		h.runAfterDelay(20, () -> {
			h.getLevel().destroyBlock(h.absolutePos(POS), true);
			List<ItemEntity> drops = h.getEntities(EntityTypes.ITEM, POS, 2);
			h.assertValueEqual(dropped(drops, ModItems.CASING_MOLD), 1, "mold dropped");
			h.assertValueEqual(dropped(drops, ModBlocks.EXTRUDER.asItem()), 1, "the extruder itself");
			h.succeed();
		});
	}

	@GameTest(maxTicks = 300)
	public void etchingUsesInputRedstoneNotEnergy(GameTestHelper h) {
		MachineBlockEntity etcher = machine(h, POS, ModBlocks.ETCHER, null, ItemStack.EMPTY,
				new ItemStack(ModItems.CIRCUIT_BOARD), new ItemStack(ModItems.COPPER_WIRE, 2),
				new ItemStack(Items.REDSTONE));
		h.runAfterDelay(110, () -> {
			h.assertTrue(etcher.getItem(MachineBlockEntity.OUTPUT).isEmpty(), "input dust is no fuel");
			h.assertValueEqual(etcher.progress(), 0, "no progress");
			etcher.setItem(MachineBlockEntity.ENERGY, new ItemStack(Items.REDSTONE));
		});
		h.runAfterDelay(230, () -> {
			h.assertTrue(etcher.getItem(MachineBlockEntity.OUTPUT).is(ModItems.SIMPLE_CIRCUIT), "circuit made");
			h.assertTrue(etcher.getItem(MachineBlockEntity.INPUT_2).isEmpty(), "input dust used once");
			h.assertTrue(etcher.getItem(MachineBlockEntity.ENERGY).isEmpty(), "energy dust used");
			h.assertValueEqual(etcher.energy(), 7, "reserve");
			h.succeed();
		});
	}

	@GameTest
	public void newMachinesNeedStonePickaxe(GameTestHelper h) {
		for (Block block : List.of(ModBlocks.MAGNETIZER, ModBlocks.ETCHER, ModBlocks.EXTRUDER)) {
			var state = block.defaultBlockState();
			h.assertFalse(new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(state), block + " wooden");
			h.assertTrue(new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(state), block + " stone");
		}
		h.assertValueEqual(ModBlocks.machines(),
				List.of(ModBlocks.COIL_WINDER, ModBlocks.MAGNETIZER, ModBlocks.ETCHER, ModBlocks.EXTRUDER),
				"machines in tab order");
		h.succeed();
	}

	@GameTest
	public void machineCraftsExist(GameTestHelper h) {
		for (String name : List.of("magnetizer", "etcher", "extruder")) {
			Optional<RecipeHolder<?>> found = h.getLevel().getServer().getRecipeManager()
					.byKey(ResourceKey.create(Registries.RECIPE, RaveCraft.id(name)));
			h.assertTrue(found.isPresent() && found.get().value() instanceof ShapedRecipe, name + " craft");
		}
		h.succeed();
	}

	private static MachineBlockEntity machine(GameTestHelper h, BlockPos pos, Block block, Item mold,
			ItemStack energy, ItemStack... inputs) {
		h.setBlock(pos, block);
		MachineBlockEntity machine = h.getBlockEntity(pos, MachineBlockEntity.class);
		for (int i = 0; i < inputs.length; i++) {
			machine.setItem(MachineBlockEntity.INPUT_0 + i, inputs[i].copy());
		}
		if (mold != null) {
			machine.setItem(MachineBlockEntity.MOLD, new ItemStack(mold));
		}
		machine.setItem(MachineBlockEntity.ENERGY, energy.copy());
		return machine;
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
