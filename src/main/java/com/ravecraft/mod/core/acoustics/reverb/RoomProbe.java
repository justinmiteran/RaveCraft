package com.ravecraft.mod.core.acoustics.reverb;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.ravecraft.mod.core.acoustics.occlusion.AcousticGrid;
import com.ravecraft.mod.core.acoustics.occlusion.AcousticMaterial;
import com.ravecraft.mod.core.acoustics.occlusion.AcousticMedium;
import com.ravecraft.mod.core.acoustics.occlusion.TraceBudget;
import com.ravecraft.mod.core.acoustics.occlusion.VoxelSample;
import com.ravecraft.mod.core.acoustics.occlusion.VoxelWalk;

/**
 * Measures the space around a point by casting rays in all directions. A ray stops at the first voxel after the
 * start that holds a solid (filling at least half of it along the ray) or a medium other than the one the ray
 * started in; the start voxel is never a hit. Resumable: {@link #run} spends one voxel of budget per voxel visited
 * and, when the budget runs out, picks up exactly there on the next call. Not thread-safe.
 */
public final class RoomProbe {
	private static final double SOLID_FILL = 0.5;

	private final AcousticGrid grid;
	private final VoxelSample sample = new VoxelSample();
	private final VoxelWalk walk = new VoxelWalk();
	private final List<RayHit> hits = new ArrayList<>();
	private double[][] directions = new double[0][];
	private double ox;
	private double oy;
	private double oz;
	private double[] direction = new double[3];
	private double rayLength;
	private int ray;
	private boolean walking;
	private @Nullable AcousticMedium startMedium;

	/**
	 * Creates an idle probe over {@code grid}.
	 *
	 * @param grid the blocks around the listener
	 */
	public RoomProbe(AcousticGrid grid) {
		this.grid = grid;
	}

	/**
	 * Begins a measure from (x, y, z) with {@code rays} rays of {@code rayLength} blocks, dropping any previous one.
	 *
	 * @param x the start's x
	 * @param y the start's y
	 * @param z the start's z
	 * @param rays how many rays to cast
	 * @param rayLength each ray's length, in blocks
	 */
	public void start(double x, double y, double z, int rays, double rayLength) {
		this.ox = x;
		this.oy = y;
		this.oz = z;
		this.rayLength = rayLength;
		this.directions = RayDirections.sphere(rays);
		this.ray = 0;
		this.walking = false;
		this.startMedium = null;
		this.hits.clear();
	}

	/**
	 * Continues the measure; true once every ray is done.
	 *
	 * @param budget the voxels left this tick
	 * @return true once every ray is done
	 */
	public boolean run(TraceBudget budget) {
		while (ray < directions.length) {
			direction = directions[ray];
			if (!walking) {
				double[] d = direction;
				walk.start(ox, oy, oz, ox + d[0] * rayLength, oy + d[1] * rayLength, oz + d[2] * rayLength);
				walking = true;
				startMedium = null;
			}
			if (!walkRay(budget)) {
				return false;
			}
			walking = false;
			ray++;
		}
		return true;
	}

	/** Walks the current ray until it ends, recording its hit; false when the budget ran out first. */
	private boolean walkRay(TraceBudget budget) {
		while (budget.take()) {
			RayHit hit = visit();
			if (hit != null) {
				hits.add(hit);
				return true;
			}
			walk.step();
		}
		return false;
	}

	/** Samples the current voxel; the ray's hit when it ends here, else null. */
	private @Nullable RayHit visit() {
		grid.sample(walk.x(), walk.y(), walk.z(), sample);
		RayHit hit = null;
		if (walk.first()) {
			AcousticMedium own = sample.medium;
			startMedium = own != null ? own : grid.defaultMedium();
		} else {
			hit = hit();
		}
		if (hit == null && walk.last()) {
			hit = new RayHit(true, rayLength, 1);
		}
		return hit;
	}

	/**
	 * The hits, one per ray in order; complete once {@link #run} returned true.
	 *
	 * @return the hits, one per ray
	 */
	public List<RayHit> hits() {
		return hits;
	}

	/** What the current voxel (never the first) does to the ray, or null when the ray goes on. */
	private @Nullable RayHit hit() {
		double distance = walk.enterT() * walk.length();
		AcousticMaterial material = sample.material;
		int axis = walk.entryAxis();
		if (material != null && sample.fill(axis) >= SOLID_FILL) {
			return new RayHit(false, distance, material.absorption(), axis, facePlane(axis), wallDistance(axis));
		}
		AcousticMedium own = sample.medium;
		if (own != null && !own.equals(startMedium)) {
			return new RayHit(false, distance, own.impedance() <= 0 ? 1 : own.surfaceAbsorption(), axis,
					facePlane(axis), wallDistance(axis));
		}
		return null;
	}

	/** The perpendicular distance from the probe origin to the face the ray crossed along {@code axis}. */
	private double wallDistance(int axis) {
		double origin = switch (axis) {
			case VoxelSample.X -> ox;
			case VoxelSample.Y -> oy;
			default -> oz;
		};
		return Math.abs(facePlane(axis) - origin);
	}

	/** The block boundary the ray crossed along {@code axis} to enter the current voxel. */
	private int facePlane(int axis) {
		int cell = switch (axis) {
			case VoxelSample.X -> walk.x();
			case VoxelSample.Y -> walk.y();
			default -> walk.z();
		};
		return direction[axis] >= 0 ? cell : cell + 1;
	}
}
