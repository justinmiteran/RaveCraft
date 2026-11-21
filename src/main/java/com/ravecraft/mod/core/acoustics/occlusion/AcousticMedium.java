package com.ravecraft.mod.core.acoustics.occlusion;

/**
 * A medium sound travels through (air, water, lava, vacuum...): its acoustic impedance, which decides how much is
 * lost at the interface with another medium, and what it absorbs per block travelled, broadband and extra treble.
 * The {@code listener} values say how a listener whose head is in this medium hears everything: a broadband loss,
 * an extra treble loss (at 4 kHz, through the occlusion low-pass) and a blur of direction from 0 (sharp) to 1 (the
 * sound seems to come from everywhere). A human ear underwater hears through the skull: quieter, duller and
 * without direction. {@code surfaceAbsorption} (0 to 1) is the share of the sound absorbed by the surface of this
 * medium seen from another one (a water surface reflects almost everything, open air absorbs everything).
 */
public record AcousticMedium(String name, double impedance, double lossDbPerBlock, double highLossDbPerBlock,
		double listenerLossDb, double listenerHighLossDb, double listenerBlur, double surfaceAbsorption) {
	/** The share a medium's surface absorbs when nothing says otherwise: a little. */
	public static final double DEFAULT_SURFACE_ABSORPTION = 0.05;

	/** A medium that does not change how a listener inside it hears. */
	public AcousticMedium(String name, double impedance, double lossDbPerBlock, double highLossDbPerBlock) {
		this(name, impedance, lossDbPerBlock, highLossDbPerBlock, 0, 0, 0, DEFAULT_SURFACE_ABSORPTION);
	}
}
