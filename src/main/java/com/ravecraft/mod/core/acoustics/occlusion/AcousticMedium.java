package com.ravecraft.mod.core.acoustics.occlusion;

/**
 * A medium sound travels through (air, water, lava, vacuum...): its acoustic impedance, which decides how much is
 * lost at the interface with another medium, and what it absorbs per block travelled, broadband and extra treble.
 */
public record AcousticMedium(String name, double impedance, double lossDbPerBlock, double highLossDbPerBlock) {
}
