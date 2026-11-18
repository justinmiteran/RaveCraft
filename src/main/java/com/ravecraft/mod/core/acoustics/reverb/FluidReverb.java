package com.ravecraft.mod.core.acoustics.reverb;

/**
 * The reverb heard with the head in a fluid (underwater, in lava), keyed by medium name. The decay, density,
 * diffusion, gain and high-frequency ratio shape the tail; the chorus gives it the slow wobble of water.
 */
public record FluidReverb(String medium, double decaySeconds, double density, double diffusion, double gain,
		double decayHfRatio, boolean chorus, double chorusRateHz, double chorusDepthMs) {
	public static final FluidReverb WATER = new FluidReverb("water", 1.2, 1, 1, 0.4, 0.3, false, 0.4, 1.5);
	public static final FluidReverb LAVA = new FluidReverb("lava", 0.6, 1, 1, 0.3, 0.2, false, 0.25, 2);
}
