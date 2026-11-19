package com.ravecraft.mod.client.audio.occlusion;

import com.ravecraft.mod.core.acoustics.occlusion.AcousticGrid;
import com.ravecraft.mod.core.acoustics.occlusion.AcousticMedium;
import com.ravecraft.mod.core.acoustics.occlusion.ListenerPerception;
import com.ravecraft.mod.core.acoustics.occlusion.OcclusionSettings;
import com.ravecraft.mod.core.acoustics.occlusion.VoxelSample;
import com.ravecraft.mod.world.acoustics.BlockAcoustics;
import com.ravecraft.mod.world.acoustics.MediumResolver;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The client's loaded world as the occlusion tracer sees it. An unloaded chunk is open air: no material, and
 * the air medium. A solid block without fluid has no medium of its own (it is in the medium around it). Client
 * thread only (it reads the level and reuses one position).
 */
public final class WorldAcousticGrid implements AcousticGrid {
	private final ClientLevel level;
	private final BlockAcoustics blocks;
	private final MediumResolver media;
	private final OcclusionSettings settings;
	/** The level's dimension medium, looked up once: neither the level nor the settings change for this grid. */
	private final AcousticMedium dimension;
	private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

	/** {@code blocks} and {@code media} must be built from {@code settings}, the tracer's own instance. */
	public WorldAcousticGrid(ClientLevel level, BlockAcoustics blocks, MediumResolver media,
			OcclusionSettings settings) {
		this.level = level;
		this.blocks = blocks;
		this.media = media;
		this.settings = settings;
		this.dimension = media.dimensionMedium(level.dimension().identifier().toString());
	}

	public ClientLevel level() {
		return level;
	}

	@Override
	public AcousticMedium defaultMedium() {
		return dimension;
	}

	/**
	 * How a listener whose eye is at {@code eye} hears: the perception of the fluid's medium when the eye is under
	 * its surface, else of the dimension's medium. Nothing in an unloaded chunk.
	 */
	public ListenerPerception perceptionAt(Vec3 eye) {
		if (!loaded(eye)) {
			return ListenerPerception.NONE;
		}
		AcousticMedium head = headMedium(eye);
		return ListenerPerception.of(head != null ? head : dimension);
	}

	/**
	 * The medium of the fluid whose surface is above the eye at {@code eye}, or null when the head is not in a fluid
	 * (or its chunk is not loaded).
	 */
	public @Nullable AcousticMedium headMedium(Vec3 eye) {
		if (!loaded(eye)) {
			return null;
		}
		pos.set(eye.x, eye.y, eye.z);
		FluidState fluid = level.getFluidState(pos);
		if (!fluid.isEmpty() && ListenerPerception.headInFluid(eye.y, pos.getY(), fluid.getHeight(level, pos))) {
			return media.medium(fluid, level.dimension().identifier().toString());
		}
		return null;
	}

	/** Whether the chunk holding {@code eye} is loaded. */
	private boolean loaded(Vec3 eye) {
		pos.set(eye.x, eye.y, eye.z);
		return level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, false) != null;
	}

	@Override
	public void sample(int x, int y, int z, VoxelSample out) {
		// ClientLevel.hasChunk always answers true: ask the chunk cache whether the chunk is really there.
		LevelChunk chunk = level.getChunkSource().getChunk(x >> 4, z >> 4, false);
		if (chunk == null) {
			out.material = null;
			out.fillX = 0;
			out.fillY = 0;
			out.fillZ = 0;
			out.medium = settings.air();
			return;
		}
		pos.set(x, y, z);
		// What Level.getBlockState answers outside the world's bounds.
		BlockState state = level.isInValidBounds(pos) ? chunk.getBlockState(pos) : Blocks.VOID_AIR.defaultBlockState();
		blocks.sample(state, media, dimension, out);
	}
}
