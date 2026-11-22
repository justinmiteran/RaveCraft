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
	private static final double MIN_DECAY = 0.1;
	private static final double MAX_DECAY = 20;
	private static final double MIN_HF_RATIO = 0.1;
	private static final double MAX_HF_RATIO = 2;
	/** The EAX ceiling of the reflections gain, +10 dB. */
	private static final double MAX_REFLECTIONS_GAIN = 3.16;
	private static final double MAX_REFLECTIONS_DELAY = 0.3;
	private static final double MAX_LATE_DELAY = 0.1;
	/** How fast the wet gain falls as the space opens. */
	private static final double OPENNESS_EXPONENT = 1.5;
	/** How much absorbing walls darken the tail. */
	private static final double HF_ABSORPTION_SHARE = 0.6;
	/** How much a rugged space weakens the early reflections. */
	private static final double SCATTER_SHARE = 0.5;
	/** The mean free path below which the echo density is full. */
	private static final double DENSE_PATH = 8;
	private static final double MIN_DIFFUSION = 0.4;
	private static final double FLUID_REFLECTIONS_SHARE = 0.5;
	private static final double FLUID_REFLECTIONS_DELAY = 0.005;
	private static final double FLUID_LATE_DELAY = 0.01;

	/** The reverb of a measured space, following the table of the spec. */
	public static ReverbParams of(RoomShape shape, ReverbSettings settings) {
		double l = Math.max(0, shape.meanFreePath());
		double openness = Math.clamp(shape.openness(), 0, 1);
		double irregularity = Math.clamp(shape.irregularity(), 0, 1);
		double gain = Math.clamp(settings.maxWetGain() * Math.pow(1 - openness, OPENNESS_EXPONENT), 0, 1);
		double travel = l / SPEED_OF_SOUND;
		return new ReverbParams(
				Math.clamp(shape.rt60(settings.maxDecaySeconds()), MIN_DECAY, MAX_DECAY),
				Math.clamp(1 - HF_ABSORPTION_SHARE * shape.hitAbsorption(), MIN_HF_RATIO, 1),
				gain,
				// Flat walls send back strong early reflections; a rugged space scatters them.
				Math.clamp(gain * (1 - SCATTER_SHARE * irregularity), 0, MAX_REFLECTIONS_GAIN),
				Math.clamp(travel, 0, MAX_REFLECTIONS_DELAY),
				Math.clamp(travel, 0, MAX_LATE_DELAY),
				l <= 0 ? 1 : Math.clamp(DENSE_PATH / l, 0, 1),
				// Flat walls give a grainy, ringing tail; a rugged space a smooth one.
				Math.clamp(MIN_DIFFUSION + (1 - MIN_DIFFUSION) * irregularity, 0, 1),
				AIR_ABSORPTION);
	}

	/** The reverb heard inside a fluid. */
	public static ReverbParams of(FluidReverb fluid) {
		double gain = Math.clamp(fluid.gain(), 0, 1);
		return new ReverbParams(
				Math.clamp(fluid.decaySeconds(), MIN_DECAY, MAX_DECAY),
				Math.clamp(fluid.decayHfRatio(), MIN_HF_RATIO, MAX_HF_RATIO),
				gain,
				Math.clamp(FLUID_REFLECTIONS_SHARE * gain, 0, MAX_REFLECTIONS_GAIN),
				FLUID_REFLECTIONS_DELAY,
				FLUID_LATE_DELAY,
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
