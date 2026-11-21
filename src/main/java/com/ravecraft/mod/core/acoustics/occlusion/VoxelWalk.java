package com.ravecraft.mod.core.acoustics.occlusion;

/**
 * Walks a straight segment voxel by voxel (Amanatides–Woo), from the voxel holding its start to the one holding its
 * end. At each voxel it tells where the segment enters and leaves it, as fractions of the segment, and the axis it
 * crossed to get in. Reusable ({@link #start} resets it) and not thread-safe.
 */
public final class VoxelWalk {
	private static final int AXES = 3;

	private final int[] cell = new int[3];
	private final int[] end = new int[3];
	private final int[] step = new int[3];
	private final double[] tMax = new double[3];
	private final double[] tDelta = new double[3];
	private double length;
	private int entryAxis;
	private double enterT;
	private double exitT;
	private int next;
	private boolean first;
	private boolean last;

	/** Starts a walk from (x0, y0, z0) to (x1, y1, z1), positioned on the voxel holding the start. */
	public void start(double x0, double y0, double z0, double x1, double y1, double z1) {
		double[] from = {x0, y0, z0};
		double[] delta = {x1 - x0, y1 - y0, z1 - z0};
		length = Math.sqrt(delta[0] * delta[0] + delta[1] * delta[1] + delta[2] * delta[2]);
		cell[0] = (int) Math.floor(x0);
		cell[1] = (int) Math.floor(y0);
		cell[2] = (int) Math.floor(z0);
		end[0] = (int) Math.floor(x1);
		end[1] = (int) Math.floor(y1);
		end[2] = (int) Math.floor(z1);
		entryAxis = 0;
		for (int a = 0; a < AXES; a++) {
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
		enterT = 0;
		first = true;
		locate();
	}

	/** Moves to the next voxel. Illegal after {@link #last()}. */
	public void step() {
		if (last) {
			throw new IllegalStateException("The walk is at its last voxel");
		}
		cell[next] += step[next];
		tMax[next] += tDelta[next];
		enterT = exitT;
		entryAxis = next;
		first = false;
		locate();
	}

	/** Finds whether the current voxel is the last one and, if not, the axis crossed to leave it and where. */
	private void locate() {
		last = cell[0] == end[0] && cell[1] == end[1] && cell[2] == end[2];
		next = -1;
		exitT = 1;
		if (!last) {
			// Lowest axis first on ties; an axis already at its end coordinate never steps again.
			for (int a = 0; a < AXES; a++) {
				if (cell[a] != end[a] && (next < 0 || tMax[a] < tMax[next])) {
					next = a;
				}
			}
			exitT = Math.clamp(tMax[next], enterT, 1);
		}
	}

	/** The current voxel's x. */
	public int x() {
		return cell[0];
	}

	/** The current voxel's y. */
	public int y() {
		return cell[1];
	}

	/** The current voxel's z. */
	public int z() {
		return cell[2];
	}

	/** The segment's length in blocks. */
	public double length() {
		return length;
	}

	/** The fraction of the segment where it enters the current voxel. */
	public double enterT() {
		return enterT;
	}

	/** The fraction of the segment where it leaves the current voxel (1 for the last one). */
	public double exitT() {
		return exitT;
	}

	/** The axis crossed to enter the current voxel ({@link VoxelSample#X}, {@code Y} or {@code Z}); the dominant axis for the first. */
	public int entryAxis() {
		return entryAxis;
	}

	/** Whether the current voxel holds the segment's start. */
	public boolean first() {
		return first;
	}

	/** Whether the current voxel holds the segment's end. */
	public boolean last() {
		return last;
	}
}
