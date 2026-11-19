package com.ravecraft.mod.core.acoustics.reverb;

/**
 * Where one probe ray ended: {@code distance} in blocks and the share of sound the surface it met absorbs. An open
 * ray met nothing within its length: its distance is the ray length and its absorption 1 (it escapes). A closed ray
 * also names the face it met: the axis it crossed ({@code faceAxis}, a {@code VoxelSample} axis) and the block
 * boundary of that face along it ({@code facePlane}); rays that met the same flat wall share both.
 */
public record RayHit(boolean open, double distance, double absorption, int faceAxis, int facePlane) {
	/** The {@code faceAxis} of a ray that met no face. */
	public static final int NO_FACE = -1;

	public RayHit(boolean open, double distance, double absorption) {
		this(open, distance, absorption, NO_FACE, 0);
	}
}
