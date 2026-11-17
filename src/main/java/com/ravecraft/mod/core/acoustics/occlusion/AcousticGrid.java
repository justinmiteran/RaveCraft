package com.ravecraft.mod.core.acoustics.occlusion;

/** The world as the occlusion tracer sees it, one voxel at a time. */
public interface AcousticGrid {
	/**
	 * Fills {@code out} with the voxel at (x, y, z): its material and fills (no material and zero fills when the voxel
	 * is open) and its medium, never null.
	 */
	void sample(int x, int y, int z, VoxelSample out);
}
