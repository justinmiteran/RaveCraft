package com.ravecraft.mod.world.acoustics;

import com.ravecraft.mod.core.acoustics.occlusion.AcousticMedium;
import com.ravecraft.mod.core.acoustics.occlusion.OcclusionSettings;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

/**
 * Maps a fluid (or the dimension, when there is none) to a medium. Always returns the instances held by the
 * settings, never copies: the tracer recognises air by equality.
 */
public final class MediumResolver {
	private final OcclusionSettings settings;

	/**
	 * With the media of {@code settings}.
	 *
	 * @param settings the occlusion settings
	 */
	public MediumResolver(OcclusionSettings settings) {
		this.settings = settings;
	}

	/**
	 * The first medium (settings order) whose fluid tag contains the fluid, else the dimension's medium.
	 *
	 * @param fluid the fluid state
	 * @param dimensionId the dimension's id
	 * @return the medium
	 */
	public AcousticMedium medium(FluidState fluid, String dimensionId) {
		AcousticMedium m = fluidMedium(fluid);
		return m != null ? m : dimensionMedium(dimensionId);
	}

	/**
	 * The medium of a dimension, by id ({@code minecraft:overworld}).
	 *
	 * @param dimensionId the dimension's id
	 * @return the medium
	 */
	public AcousticMedium dimensionMedium(String dimensionId) {
		return settings.dimensionMedium(dimensionId);
	}

	/**
	 * A voxel's own medium: its fluid's; else, without fluid, none for a voxel holding a material (a solid takes the
	 * medium around it: a stone block underwater is in the water) and {@code dimension} for an open one. A fluid no
	 * medium claims counts as {@code dimension}.
	 *
	 * @param fluid the voxel's fluid
	 * @param solid true when the voxel holds a material
	 * @param dimension the dimension's medium
	 * @return the medium, or null
	 */
	public @Nullable AcousticMedium voxelMedium(FluidState fluid, boolean solid, AcousticMedium dimension) {
		AcousticMedium m = fluidMedium(fluid);
		if (m != null) {
			return m;
		}
		return fluid.isEmpty() && solid ? null : dimension;
	}

	private @Nullable AcousticMedium fluidMedium(FluidState fluid) {
		if (!fluid.isEmpty()) {
			for (AcousticMedium m : settings.media()) {
				if (fluid.is(AcousticTags.medium(m.name()))) {
					return m;
				}
			}
		}
		return null;
	}
}
