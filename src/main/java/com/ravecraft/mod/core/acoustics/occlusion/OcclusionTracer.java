package com.ravecraft.mod.core.acoustics.occlusion;

import org.jspecify.annotations.Nullable;

/**
 * Walks a straight segment voxel by voxel (Amanatides–Woo) and adds up what it loses: the materials it crosses
 * (weighted by their fill along the entry axis), the media it travels through (except air, whose absorption
 * {@code Propagation} applies) and the interfaces between media. The start and end voxels bring no material (the
 * speaker's block, the listener's head). A medium of impedance 0 anywhere on the way mutes the segment. Both totals
 * are capped at {@link OcclusionSettings#maxLossDb()}. Not thread-safe: one tracer per thread.
 */
public final class OcclusionTracer {
	/** Voxels crossed over less than this many blocks (a ray grazing an edge or a corner) add nothing. */
	private static final double MIN_LENGTH = 1e-9;

	private final AcousticGrid grid;
	private final OcclusionSettings settings;
	private final AcousticMedium air;
	private final VoxelSample sample = new VoxelSample();
	private final int[] cell = new int[3];
	private final int[] end = new int[3];
	private final int[] step = new int[3];
	private final double[] tMax = new double[3];
	private final double[] tDelta = new double[3];

	public OcclusionTracer(AcousticGrid grid, OcclusionSettings settings) {
		this.grid = grid;
		this.settings = settings;
		this.air = settings.air();
	}

	/**
	 * The loss from (x0, y0, z0) to (x1, y1, z1), or null when {@code budget} ran out before the end voxel; one voxel
	 * of budget is taken per voxel visited.
	 */
	public @Nullable PathLoss trace(double x0, double y0, double z0, double x1, double y1, double z1,
			TraceBudget budget) {
		double[] from = {x0, y0, z0};
		double[] delta = {x1 - x0, y1 - y0, z1 - z0};
		double length = Math.sqrt(delta[0] * delta[0] + delta[1] * delta[1] + delta[2] * delta[2]);
		cell[0] = (int) Math.floor(x0);
		cell[1] = (int) Math.floor(y0);
		cell[2] = (int) Math.floor(z0);
		end[0] = (int) Math.floor(x1);
		end[1] = (int) Math.floor(y1);
		end[2] = (int) Math.floor(z1);
		int entryAxis = 0;
		for (int a = 0; a < 3; a++) {
			double d = delta[a];
			if (d > 0) {
				step[a] = 1;
				tMax[a] = (cell[a] + 1 - from[a]) / d;
				tDelta[a] = 1 / d;
			} else if (d < 0) {
				step[a] = -1;
				tMax[a] = (from[a] - cell[a]) / -d;
				tDelta[a] = -1 / d;
			} else {
				step[a] = 0;
				tMax[a] = Double.POSITIVE_INFINITY;
				tDelta[a] = Double.POSITIVE_INFINITY;
			}
			if (Math.abs(d) > Math.abs(delta[entryAxis])) {
				entryAxis = a;
			}
		}

		double loss = 0;
		double high = 0;
		double t = 0;
		boolean first = true;
		@Nullable AcousticMedium previous = null;
		while (true) {
			if (!budget.take()) {
				return null;
			}
			boolean last = cell[0] == end[0] && cell[1] == end[1] && cell[2] == end[2];
			int next = -1;
			double tExit = 1;
			if (!last) {
				// Lowest axis first on ties; an axis already at its end coordinate never steps again.
				for (int a = 0; a < 3; a++) {
					if (cell[a] != end[a] && (next < 0 || tMax[a] < tMax[next])) {
						next = a;
					}
				}
				tExit = Math.clamp(tMax[next], t, 1);
			}
			double inside = (tExit - t) * length;

			if (inside >= MIN_LENGTH) {
				grid.sample(cell[0], cell[1], cell[2], sample);
				AcousticMedium medium = sample.medium != null ? sample.medium : air;
				if (medium.impedance() <= 0) {
					return PathLoss.mute(length);
				}
				AcousticMaterial material = sample.material;
				if (material != null && !first && !last) {
					double weight = sample.fill(entryAxis) * Math.min(1, inside);
					loss += material.lossDb() * weight;
					high += material.highLossDb() * weight;
				}
				if (!medium.name().equals(air.name())) {
					loss += medium.lossDbPerBlock() * inside;
					high += medium.highLossDbPerBlock() * inside;
				}
				if (previous != null && !previous.equals(medium)) {
					double interfaceLoss = Interfaces.lossDb(previous, medium, settings.interfaceScale());
					if (Double.isInfinite(interfaceLoss)) {
						return PathLoss.mute(length);
					}
					loss += interfaceLoss;
					high += Interfaces.highLossDb(interfaceLoss, settings.interfaceHighRatio());
				}
				previous = medium;
			}

			if (last) {
				break;
			}
			cell[next] += step[next];
			tMax[next] += tDelta[next];
			t = tExit;
			entryAxis = next;
			first = false;
		}
		double max = settings.maxLossDb();
		return new PathLoss(Math.min(loss, max), Math.min(high, max), length);
	}
}
