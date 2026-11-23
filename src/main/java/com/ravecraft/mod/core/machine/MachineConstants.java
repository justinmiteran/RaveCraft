package com.ravecraft.mod.core.machine;

/** Shared numbers of the machines (spec 5.1): energy units, operation time and recipe size. */
public final class MachineConstants {
	/** Operations given by one redstone dust. */
	public static final int OPERATIONS_PER_DUST = 8;
	/** Operations given by one redstone block (nine dusts). */
	public static final int OPERATIONS_PER_BLOCK = 72;
	/** Largest energy reserve: one redstone block. */
	public static final int MAX_ENERGY = OPERATIONS_PER_BLOCK;
	/** Duration of an operation when the recipe does not say, in ticks. */
	public static final int DEFAULT_PROCESSING_TICKS = 100;
	/** Most distinct ingredients of a machine recipe, one per input slot. */
	public static final int MAX_INGREDIENTS = 3;

	private MachineConstants() {
	}
}
