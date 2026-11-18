package com.ravecraft.mod.gametest;

import com.ravecraft.mod.core.acoustics.occlusion.AcousticMaterial;
import com.ravecraft.mod.core.acoustics.occlusion.AcousticMedium;
import com.ravecraft.mod.core.acoustics.occlusion.OcclusionSettings;
import com.ravecraft.mod.core.acoustics.occlusion.VoxelSample;
import com.ravecraft.mod.registry.ModBlocks;
import com.ravecraft.mod.world.acoustics.BlockAcoustics;
import com.ravecraft.mod.world.acoustics.MediumResolver;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

public class BlockAcousticsGameTest {
	private static final String OVERWORLD = "minecraft:overworld";

	private final BlockAcoustics acoustics = new BlockAcoustics(OcclusionSettings.DEFAULT);
	private final MediumResolver media = new MediumResolver(OcclusionSettings.DEFAULT);

	private void assertMaterial(GameTestHelper h, Block block, String expected) {
		AcousticMaterial m = acoustics.material(block.defaultBlockState());
		h.assertTrue(m != null && m.name().equals(expected), block + " should be " + expected + " but is " + m);
		h.succeed();
	}

	private void assertNoMaterial(GameTestHelper h, Block block) {
		h.assertTrue(acoustics.material(block.defaultBlockState()) == null, block + " should have no material");
		h.succeed();
	}

	private void assertFill(GameTestHelper h, BlockState state, Direction.Axis axis, double expected) {
		double fill = acoustics.fill(state, axis);
		h.assertTrue(Math.abs(fill - expected) < 1e-6, state + " fill " + axis + " should be " + expected + " but is " + fill);
	}

	@GameTest
	public void stoneIsStone(GameTestHelper h) {
		assertMaterial(h, Blocks.STONE, "stone");
	}

	@GameTest
	public void planksAreWood(GameTestHelper h) {
		assertMaterial(h, Blocks.OAK_PLANKS, "wood");
	}

	@GameTest
	public void glassIsGlass(GameTestHelper h) {
		assertMaterial(h, Blocks.GLASS, "glass");
	}

	@GameTest
	public void ironBlockIsMetal(GameTestHelper h) {
		assertMaterial(h, Blocks.IRON_BLOCK, "metal");
	}

	@GameTest
	public void whiteWoolIsWool(GameTestHelper h) {
		assertMaterial(h, Blocks.WOOL.white(), "wool");
	}

	@GameTest
	public void sandIsEarth(GameTestHelper h) {
		assertMaterial(h, Blocks.SAND, "earth");
	}

	@GameTest
	public void snowBlockIsSnow(GameTestHelper h) {
		assertMaterial(h, Blocks.SNOW_BLOCK, "snow");
	}

	@GameTest
	public void leavesAreFoliageDespiteGrassSound(GameTestHelper h) {
		assertMaterial(h, Blocks.OAK_LEAVES, "foliage");
	}

	@GameTest
	public void unknownSolidFallsBackToStone(GameTestHelper h) {
		assertMaterial(h, Blocks.SHROOMLIGHT, "stone");
	}

	@GameTest
	public void noCollisionNoMaterial(GameTestHelper h) {
		assertNoMaterial(h, Blocks.SHORT_GRASS);
		assertNoMaterial(h, Blocks.TORCH);
		assertNoMaterial(h, Blocks.AIR);
	}

	@GameTest
	public void bottomSlabFillsHalf(GameTestHelper h) {
		BlockState slab = Blocks.STONE_SLAB.defaultBlockState();
		assertFill(h, slab, Direction.Axis.Y, 0.5);
		assertFill(h, slab, Direction.Axis.X, 0.5);
		h.succeed();
	}

	@GameTest
	public void closedDoorSealsItsAxis(GameTestHelper h) {
		BlockState closed = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH)
				.setValue(DoorBlock.OPEN, false);
		assertFill(h, closed, Direction.Axis.Z, 1.0);
		BlockState open = closed.setValue(DoorBlock.OPEN, true);
		double fill = acoustics.fill(open, Direction.Axis.Z);
		h.assertTrue(fill < 0.25, "open door fill Z should be < 0.25 but is " + fill);
		h.succeed();
	}

	@GameTest
	public void glassPaneSealsFaceOn(GameTestHelper h) {
		BlockState pane = Blocks.GLASS_PANE.defaultBlockState().setValue(CrossCollisionBlock.EAST, true)
				.setValue(CrossCollisionBlock.WEST, true);
		assertFill(h, pane, Direction.Axis.Z, 1.0);
		h.succeed();
	}

	@GameTest
	public void fenceDoesNotSeal(GameTestHelper h) {
		BlockState fence = Blocks.OAK_FENCE.defaultBlockState().setValue(CrossCollisionBlock.EAST, true)
				.setValue(CrossCollisionBlock.WEST, true);
		double fill = acoustics.fill(fence, Direction.Axis.Z);
		h.assertTrue(fill < 0.5, "fence fill Z should be < 0.5 but is " + fill);
		h.succeed();
	}

	@GameTest
	public void wallFillStaysWithinOne(GameTestHelper h) {
		BlockState wall = Blocks.COBBLESTONE_WALL.defaultBlockState().setValue(WallBlock.NORTH, WallSide.LOW)
				.setValue(WallBlock.SOUTH, WallSide.LOW);
		double fill = acoustics.fill(wall, Direction.Axis.X);
		h.assertTrue(fill <= 1.0, "wall fill X should be <= 1 but is " + fill);
		h.succeed();
	}

	@GameTest
	public void carpetCountsByVolume(GameTestHelper h) {
		assertFill(h, Blocks.CARPET.white().defaultBlockState(), Direction.Axis.Y, 1.0 / 16.0);
		h.succeed();
	}

	@GameTest
	public void waterloggedSlabIsWoodInWater(GameTestHelper h) {
		BlockPos pos = new BlockPos(1, 1, 1);
		h.setBlock(pos, Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
		BlockState state = h.getBlockState(pos);
		AcousticMaterial m = acoustics.material(state);
		h.assertTrue(m != null && m.name().equals("wood"), "slab is wood but is " + m);
		h.assertValueEqual(media.medium(state.getFluidState(), OVERWORLD).name(), "water", "medium");
		h.assertTrue(media.medium(state.getFluidState(), OVERWORLD) == OcclusionSettings.DEFAULT.medium("water"),
				"water is the settings' own instance");
		h.succeed();
	}

	@GameTest
	public void lavaIsLava(GameTestHelper h) {
		h.assertValueEqual(media.medium(Blocks.LAVA.defaultBlockState().getFluidState(), OVERWORLD).name(), "lava",
				"lava medium");
		h.assertValueEqual(media.medium(Fluids.EMPTY.defaultFluidState(), OVERWORLD).name(), "air", "empty fluid");
		h.assertTrue(media.medium(Fluids.EMPTY.defaultFluidState(), OVERWORLD) == OcclusionSettings.DEFAULT.air(),
				"air is the settings' own instance");
		h.assertTrue(media.medium(Fluids.WATER.defaultFluidState(), OVERWORLD) == OcclusionSettings.DEFAULT.medium("water"),
				"water fluid is the settings' water");
		h.succeed();
	}

	private @Nullable AcousticMedium voxelMedium(BlockState state) {
		VoxelSample out = new VoxelSample();
		acoustics.sample(state, media, media.dimensionMedium(OVERWORLD), out);
		return out.medium;
	}

	@GameTest
	public void solidWithoutFluidHasNoMediumOfItsOwn(GameTestHelper h) {
		OcclusionSettings s = OcclusionSettings.DEFAULT;
		BlockState stone = Blocks.STONE.defaultBlockState();
		h.assertTrue(voxelMedium(stone) == null, "stone: " + voxelMedium(stone));
		BlockState speaker = ModBlocks.PORTABLE_SPEAKER.defaultBlockState();
		h.assertTrue(voxelMedium(speaker) == null, "speaker: " + voxelMedium(speaker));
		h.assertTrue(voxelMedium(Blocks.AIR.defaultBlockState()) == s.air(), "air block is the dimension's air");
		h.assertTrue(voxelMedium(Blocks.SHORT_GRASS.defaultBlockState()) == s.air(), "grass is open: air");
		h.assertTrue(voxelMedium(Blocks.WATER.defaultBlockState()) == s.medium("water"), "water block is water");
		h.assertTrue(voxelMedium(Blocks.SEAGRASS.defaultBlockState()) == s.medium("water"), "seagrass is in water");
		h.assertTrue(voxelMedium(Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true))
				== s.medium("water"), "a waterlogged slab is in water");
		h.assertTrue(media.dimensionMedium(OVERWORLD) == s.air(), "the overworld is the settings' air");
		h.succeed();
	}

	@GameTest
	public void throwingShapeFallsBackToAFullCube(GameTestHelper h) {
		int[] calls = {0};
		BlockAcoustics throwing = new BlockAcoustics(OcclusionSettings.DEFAULT, state -> {
			calls[0]++;
			throw new IllegalStateException("a modded block reading a block entity that is not there");
		});
		BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
		AcousticMaterial m = throwing.material(planks);
		h.assertTrue(m != null && m.name().equals("wood"), "falls back to the resolved material, wood, but is " + m);
		for (Direction.Axis axis : Direction.Axis.values()) {
			h.assertValueEqual(throwing.fill(planks, axis), 1.0, "full cube along " + axis);
		}
		// Redstone dust has no tag and a footstep sound no material claims: stone.
		AcousticMaterial unresolved = throwing.material(Blocks.REDSTONE_WIRE.defaultBlockState());
		h.assertTrue(unresolved != null && unresolved.name().equals("stone"), "unresolved is stone, but is "
				+ unresolved);
		throwing.material(planks);
		h.assertValueEqual(calls[0], 2, "the fallback is cached");
		h.succeed();
	}
}
