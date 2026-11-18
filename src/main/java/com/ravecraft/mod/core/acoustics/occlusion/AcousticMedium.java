package com.ravecraft.mod.core.acoustics.occlusion;

/**
 * A medium sound travels through (air, water, lava, vacuum...): its acoustic impedance, which decides how much is
 * lost at the interface with another medium, and what it absorbs per block travelled, broadband and extra treble.
 * The {@code listener} values say how a listener whose head is in this medium hears everything: a broadband loss,
 * an extra treble loss (at 4 kHz, through the occlusion low-pass) and a blur of direction from 0 (sharp) to 1 (the
 * sound seems to come from everywhere). A human ear underwater hears through the skull: quieter, duller and
 * without direction.
 */
public record AcousticMedium(String name, double impedance, double lossDbPerBlock, double highLossDbPerBlock,
		double listenerLossDb, double listenerHighLossDb, double listenerBlur) {
	/** A medium that does not change how a listener inside it hears. */
	public AcousticMedium(String name, double impedance, double lossDbPerBlock, double highLossDbPerBlock) {
		this(name, impedance, lossDbPerBlock, highLossDbPerBlock, 0, 0, 0);
	}
}
