package com.ravecraft.mod.core.acoustics.occlusion;

/** How many voxels the tracer may still visit, shared by every trace of a tick. Not thread-safe. */
public final class TraceBudget {
	private final int voxels;
	private int used;

	/**
	 * Creates an unspent budget.
	 *
	 * @param voxels the voxels it allows; a negative count allows none
	 */
	public TraceBudget(int voxels) {
		this.voxels = Math.max(0, voxels);
	}

	/**
	 * Consumes one voxel; false, consuming nothing, once the budget is spent.
	 *
	 * @return true when a voxel was taken
	 */
	public boolean take() {
		if (used >= voxels) {
			return false;
		}
		used++;
		return true;
	}

	/**
	 * True once every voxel has been taken.
	 *
	 * @return true once the budget is spent
	 */
	public boolean exhausted() {
		return used >= voxels;
	}

	/**
	 * The voxels consumed so far.
	 *
	 * @return the voxels consumed
	 */
	public int used() {
		return used;
	}
}
