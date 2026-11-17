package com.ravecraft.mod.core.acoustics.occlusion;

import org.jspecify.annotations.Nullable;

/**
 * What one voxel holds, filled in by {@link AcousticGrid#sample}: the solid material (or none), how much of the voxel
 * it fills seen along each axis (0 to 1), and the medium at that position (the fluid it contains, or the surrounding
 * medium). Mutable and reused by the tracer to avoid allocating per voxel.
 */
public final class VoxelSample {
	public static final int X = 0;
	public static final int Y = 1;
	public static final int Z = 2;

	public @Nullable AcousticMaterial material;
	public double fillX;
	public double fillY;
	public double fillZ;
	public @Nullable AcousticMedium medium;

	/** The fill seen by a ray crossing the voxel along {@code axis} (0 = X, 1 = Y, 2 = Z). */
	public double fill(int axis) {
		return switch (axis) {
			case X -> fillX;
			case Y -> fillY;
			case Z -> fillZ;
			default -> throw new IllegalArgumentException("axis " + axis);
		};
	}
}
