package com.ravecraft.mod.core.acoustics.reverb;

/**
 * The OpenAL EFX EAX-reverb parameters of a space, always within the EAX bounds. Built from a measured
 * {@link RoomShape} or from a fluid preset, and blended with {@link #toward} to move smoothly between two spaces.
 */
public record ReverbParams(double decayTime, double decayHfRatio, double gain, double reflectionsGain,
		double reflectionsDelay, double lateReverbDelay, double density, double diffusion,
		double airAbsorptionGainHf) {
	public static final ReverbParams SILENT = new ReverbParams(1.49, 0.83, 0, 0, 0.007, 0.011, 1, 1, 0.994);

	private static final double SPEED_OF_SOUND = 343;
	private static final double AIR_ABSORPTION = 0.994;

	/** The reverb of a measured space, following the table of the spec. */
	public static ReverbParams of(RoomShape shape, ReverbSettings settings) {
		double l = Math.max(0, shape.meanFreePath());
		double openness = Math.clamp(shape.openness(), 0, 1);
		double irregularity = Math.clamp(shape.irregularity(), 0, 1);
		double gain = Math.clamp(settings.maxWetGain() * Math.pow(1 - openness, 1.5), 0, 1);
		double travel = l / SPEED_OF_SOUND;
		return new ReverbParams(
				Math.clamp(shape.rt60(settings.maxDecaySeconds()), 0.1, 20),
				Math.clamp(1 - 0.6 * shape.hitAbsorption(), 0.1, 1),
				gain,
				// Flat walls send back strong early reflections; a rugged space scatters them.
				Math.clamp(gain * (1 - 0.5 * irregularity), 0, 3.16),
				Math.clamp(travel, 0, 0.3),
				Math.clamp(travel, 0, 0.1),
				l <= 0 ? 1 : Math.clamp(8 / l, 0, 1),
				// Flat walls give a grainy, ringing tail; a rugged space a smooth one.
				Math.clamp(0.4 + 0.6 * irregularity, 0, 1),
				AIR_ABSORPTION);
	}

	/** The reverb heard inside a fluid. */
	public static ReverbParams of(FluidReverb fluid) {
		double gain = Math.clamp(fluid.gain(), 0, 1);
		return new ReverbParams(
				Math.clamp(fluid.decaySeconds(), 0.1, 20),
				Math.clamp(fluid.decayHfRatio(), 0.1, 2),
				gain,
				Math.clamp(0.5 * gain, 0, 3.16),
				0.005,
				0.01,
				Math.clamp(fluid.density(), 0, 1),
				Math.clamp(fluid.diffusion(), 0, 1),
				AIR_ABSORPTION);
	}

	/** Field-wise linear blend from this to {@code target}; {@code fraction} is clamped to 0..1. */
	public ReverbParams toward(ReverbParams target, double fraction) {
		double f = Math.clamp(fraction, 0, 1);
		return new ReverbParams(
				mix(decayTime, target.decayTime, f),
				mix(decayHfRatio, target.decayHfRatio, f),
				mix(gain, target.gain, f),
				mix(reflectionsGain, target.reflectionsGain, f),
				mix(reflectionsDelay, target.reflectionsDelay, f),
				mix(lateReverbDelay, target.lateReverbDelay, f),
				mix(density, target.density, f),
				mix(diffusion, target.diffusion, f),
				mix(airAbsorptionGainHf, target.airAbsorptionGainHf, f));
	}

	private static double mix(double from, double to, double f) {
		return from * (1 - f) + to * f;
	}
}
