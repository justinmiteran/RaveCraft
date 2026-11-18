package com.ravecraft.mod.world.acoustics;

import com.ravecraft.mod.core.acoustics.occlusion.AcousticMedium;
import com.ravecraft.mod.core.acoustics.occlusion.OcclusionSettings;
import net.minecraft.world.level.material.FluidState;

/**
 * Maps a fluid (or the dimension, when there is none) to a medium. Always returns the instances held by the
 * settings, never copies: the tracer recognises air by equality.
 */
public final class MediumResolver {
	private final OcclusionSettings settings;

	public MediumResolver(OcclusionSettings settings) {
		this.settings = settings;
	}

	/** The first medium (settings order) whose fluid tag contains the fluid, else the dimension's medium. */
	public AcousticMedium medium(FluidState fluid, String dimensionId) {
		if (!fluid.isEmpty()) {
			for (AcousticMedium m : settings.media()) {
				if (fluid.is(AcousticTags.medium(m.name()))) {
					return m;
				}
			}
		}
		return settings.dimensionMedium(dimensionId);
	}
}
