package com.ravecraft.mod.core.acoustics.occlusion;

/** How many voxels the tracer may still visit, shared by every trace of a tick. Not thread-safe. */
public final class TraceBudget {
	private final int voxels;
	private int used;

	public TraceBudget(int voxels) {
		this.voxels = Math.max(0, voxels);
	}

	/** Consumes one voxel; false, consuming nothing, once the budget is spent. */
	public boolean take() {
		if (used >= voxels) {
			return false;
		}
		used++;
		return true;
	}

	/** True once every voxel has been taken. */
	public boolean exhausted() {
		return used >= voxels;
	}

	public int used() {
		return used;
	}
}
