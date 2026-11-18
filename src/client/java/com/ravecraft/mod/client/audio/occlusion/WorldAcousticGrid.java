package com.ravecraft.mod.client.audio.occlusion;

import com.ravecraft.mod.core.acoustics.occlusion.AcousticGrid;
import com.ravecraft.mod.core.acoustics.occlusion.OcclusionSettings;
import com.ravecraft.mod.core.acoustics.occlusion.VoxelSample;
import com.ravecraft.mod.world.acoustics.BlockAcoustics;
import com.ravecraft.mod.world.acoustics.MediumResolver;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The client's loaded world as the occlusion tracer sees it. An unloaded chunk is open air: no material, and
 * the air medium. Client thread only (it reads the level and reuses one position).
 */
public final class WorldAcousticGrid implements AcousticGrid {
	private final ClientLevel level;
	private final BlockAcoustics blocks;
	private final MediumResolver media;
	private final OcclusionSettings settings;
	private final String dimensionId;
	private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

	/** {@code blocks} and {@code media} must be built from {@code settings}, the tracer's own instance. */
	public WorldAcousticGrid(ClientLevel level, BlockAcoustics blocks, MediumResolver media,
			OcclusionSettings settings) {
		this.level = level;
		this.blocks = blocks;
		this.media = media;
		this.settings = settings;
		this.dimensionId = level.dimension().identifier().toString();
	}

	public ClientLevel level() {
		return level;
	}

	@Override
	public void sample(int x, int y, int z, VoxelSample out) {
		if (!level.hasChunk(x >> 4, z >> 4)) {
			out.material = null;
			out.fillX = 0;
			out.fillY = 0;
			out.fillZ = 0;
			out.medium = settings.air();
			return;
		}
		BlockState state = level.getBlockState(pos.set(x, y, z));
		blocks.sample(state, media.medium(state.getFluidState(), dimensionId), out);
	}
}
