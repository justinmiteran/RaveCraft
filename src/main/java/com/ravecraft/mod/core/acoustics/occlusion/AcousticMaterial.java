package com.ravecraft.mod.core.acoustics.occlusion;

/**
 * What a solid block sounds like to the occlusion model: {@code lossDb} is the broadband loss of one block of it,
 * {@code highLossDb} the extra treble loss on top of that.
 */
public record AcousticMaterial(String name, double lossDb, double highLossDb) {
}
