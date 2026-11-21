package com.ravecraft.mod.gametest;

import com.ravecraft.mod.core.acoustics.occlusion.AcousticGrid;
import com.ravecraft.mod.core.acoustics.occlusion.AcousticMedium;
import com.ravecraft.mod.core.acoustics.occlusion.OcclusionSettings;
import com.ravecraft.mod.core.acoustics.occlusion.TraceBudget;
import com.ravecraft.mod.core.acoustics.occlusion.VoxelSample;
import com.ravecraft.mod.core.acoustics.reverb.RoomProbe;
import com.ravecraft.mod.core.acoustics.reverb.RoomShape;
import com.ravecraft.mod.world.acoustics.BlockAcoustics;
import com.ravecraft.mod.world.acoustics.MediumResolver;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

public class RoomProbeGameTest {
	private static final String OVERWORLD = "minecraft:overworld";
	private static final int RAYS = 32;
	private static final double RAY_LENGTH = 64;

	/** The real world as the room probe sees it, read from the server level. */
	private static final class ServerGrid implements AcousticGrid {
		private final ServerLevel level;
		private final BlockAcoustics blocks = new BlockAcoustics(OcclusionSettings.DEFAULT);
		private final MediumResolver media = new MediumResolver(OcclusionSettings.DEFAULT);
		private final AcousticMedium dimension = media.dimensionMedium(OVERWORLD);
		private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		ServerGrid(ServerLevel level) {
			this.level = level;
		}

		@Override
		public void sample(int x, int y, int z, VoxelSample out) {
			blocks.sample(level.getBlockState(pos.set(x, y, z)), media, dimension, out);
		}

		@Override
		public AcousticMedium defaultMedium() {
			return dimension;
		}
	}

	private static boolean edge(int v, int min, int max) {
		return v == min || v == max;
	}

	@GameTest
	public void stoneRoomIsClosed(GameTestHelper h) {
		// A 7x5x7 hollow stone shell: x and z from 0 to 6, y from 1 to 5, air inside.
		for (int i = 0; i < 7 * 5 * 7; i++) {
			int x = i / 35;
			int y = 1 + i / 7 % 5;
			int z = i % 7;
			boolean wall = edge(x, 0, 6) || edge(y, 1, 5) || edge(z, 0, 6);
			h.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
		}
		BlockPos centre = h.absolutePos(new BlockPos(3, 3, 3));
		RoomProbe probe = new RoomProbe(new ServerGrid(h.getLevel()));
		probe.start(centre.getX() + 0.5, centre.getY() + 0.5, centre.getZ() + 0.5, RAYS, RAY_LENGTH);
		h.assertTrue(probe.run(new TraceBudget(Integer.MAX_VALUE)), "the probe finishes");
		h.assertValueEqual(probe.hits().size(), RAYS, "one hit per ray");
		RoomShape shape = RoomShape.of(probe.hits(), RAY_LENGTH);
		h.assertValueEqual(shape.openness(), 0.0, "openness");
		double rt60 = shape.rt60(6);
		h.assertTrue(rt60 > 1, "a stone room rings for more than 1 s, but rt60 is " + rt60 + " (" + shape + ")");
		h.succeed();
	}
}
