package com.ravecraft.mod.core.acoustics.occlusion;

/**
 * What a solid block sounds like to the occlusion model: {@code lossDb} is the broadband loss of one block of it,
 * {@code highLossDb} the extra treble loss on top of that. {@code absorption} (0 to 1) is the share of the sound a
 * surface of it absorbs instead of reflecting back into the room.
 */
public record AcousticMaterial(String name, double lossDb, double highLossDb, double absorption) {
	/** A material that absorbs a tenth of what hits it. */
	public AcousticMaterial(String name, double lossDb, double highLossDb) {
		this(name, lossDb, highLossDb, 0.1);
	}
}
