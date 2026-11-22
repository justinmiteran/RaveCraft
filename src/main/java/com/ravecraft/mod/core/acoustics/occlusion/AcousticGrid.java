package com.ravecraft.mod.core.acoustics.occlusion;

/** The world as the occlusion tracer sees it, one voxel at a time. */
public interface AcousticGrid {
	/**
	 * Fills {@code out} with the voxel at (x, y, z): its material and fills (no material and zero fills when the voxel
	 * is open) and its own medium: the fluid it holds, else the {@linkplain #defaultMedium() dimension's medium} for an
	 * open voxel, and null for a solid voxel without fluid (it takes the medium around it). Every medium is an instance
	 * held by the tracer's settings.
	 *
	 * @param x the voxel's x
	 * @param y the voxel's y
	 * @param z the voxel's z
	 * @param out receives the voxel
	 */
	void sample(int x, int y, int z, VoxelSample out);

	/**
	 * The dimension's medium, for a segment that only crosses voxels without a medium of their own; an instance held
	 * by the tracer's settings.
	 *
	 * @return the dimension's medium
	 */
	AcousticMedium defaultMedium();
}
