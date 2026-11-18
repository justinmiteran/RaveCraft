package com.ravecraft.mod.core.acoustics.occlusion;

import org.jspecify.annotations.Nullable;

/**
 * How the medium around the listener's head changes everything they hear: a broadband loss, an extra treble loss
 * (at 4 kHz) and a blur of direction (0 sharp, 1 everywhere). Added on top of each stream's own occlusion.
 */
public record ListenerPerception(double lossDb, double highLossDb, double blur) {
	public static final ListenerPerception NONE = new ListenerPerception(0, 0, 0);

	/** The perception of a head in {@code medium}; none without a medium. */
	public static ListenerPerception of(@Nullable AcousticMedium medium) {
		if (medium == null) {
			return NONE;
		}
		ListenerPerception p = new ListenerPerception(medium.listenerLossDb(), medium.listenerHighLossDb(),
				medium.listenerBlur());
		return p.equals(NONE) ? NONE : p;
	}

	/**
	 * Whether an eye at height {@code eyeY} is under the surface of a fluid filling {@code fluidHeight} (0 to 1) of
	 * the block at {@code blockY}: a fluid's surface sits below the top of its block.
	 */
	public static boolean headInFluid(double eyeY, int blockY, double fluidHeight) {
		return eyeY < blockY + fluidHeight;
	}
}
