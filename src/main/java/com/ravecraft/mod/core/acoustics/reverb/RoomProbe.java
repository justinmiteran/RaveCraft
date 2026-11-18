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
	private double rayLength;
	private int ray;
	private boolean walking;
	private @Nullable AcousticMedium startMedium;

	public RoomProbe(AcousticGrid grid) {
		this.grid = grid;
	}

	/** Begins a measure from (x, y, z) with {@code rays} rays of {@code rayLength} blocks, dropping any previous one. */
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

	/** Continues the measure; true once every ray is done. */
	public boolean run(TraceBudget budget) {
		while (ray < directions.length) {
			double[] d = directions[ray];
			if (!walking) {
				walk.start(ox, oy, oz, ox + d[0] * rayLength, oy + d[1] * rayLength, oz + d[2] * rayLength);
				walking = true;
				startMedium = null;
			}
			while (true) {
				if (!budget.take()) {
					return false;
				}
				grid.sample(walk.x(), walk.y(), walk.z(), sample);
				RayHit hit = walk.first() ? null : hit();
				if (walk.first()) {
					AcousticMedium own = sample.medium;
					startMedium = own != null ? own : grid.defaultMedium();
				}
				if (hit != null) {
					hits.add(hit);
					break;
				}
				if (walk.last()) {
					hits.add(new RayHit(true, rayLength, 1));
					break;
				}
				walk.step();
			}
			walking = false;
			ray++;
		}
		return true;
	}

	/** The hits, one per ray in order; complete once {@link #run} returned true. */
	public List<RayHit> hits() {
		return hits;
	}

	/** What the current voxel (never the first) does to the ray, or null when the ray goes on. */
	private @Nullable RayHit hit() {
		double distance = walk.enterT() * walk.length();
		AcousticMaterial material = sample.material;
		if (material != null && sample.fill(walk.entryAxis()) >= SOLID_FILL) {
			return new RayHit(false, distance, material.absorption());
		}
		AcousticMedium own = sample.medium;
		if (own != null && !own.equals(startMedium)) {
			return new RayHit(false, distance, own.impedance() <= 0 ? 1 : own.surfaceAbsorption());
		}
		return null;
	}
}
