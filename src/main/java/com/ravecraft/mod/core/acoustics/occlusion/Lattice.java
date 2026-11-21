package com.ravecraft.mod.core.acoustics.occlusion;

/** Geometry on the block grid for the tracer: voxel keys, the 26 neighbour directions, distances and bends. */
final class Lattice {
	/** The offset from a voxel's corner to its centre, on each axis. */
	static final double CENTRE = 0.5;
	/** The 26 unit directions to the neighbours of a voxel, in a fixed order. */
	static final double[][] DIRECTIONS = directions();

	/** Voxels per side of the cube made by a voxel and its neighbours. */
	private static final int SIDE = 3;
	private static final int CUBE = SIDE * SIDE * SIDE;
	/** Bits of each coordinate in a voxel key. */
	private static final int KEY_BITS = 21;
	private static final int KEY_MASK = (1 << KEY_BITS) - 1;

	private Lattice() {
	}

	/** A key for the voxel holding {@code p}, unique within ±2^20 blocks. */
	static long voxelKey(double[] p) {
		return key((int) Math.floor(p[0]), (int) Math.floor(p[1]), (int) Math.floor(p[2]));
	}

	/** A key for the voxel (x, y, z), unique within ±2^20 blocks. */
	static long key(int x, int y, int z) {
		long high = (long) (x & KEY_MASK) << (2 * KEY_BITS);
		long middle = (long) (y & KEY_MASK) << KEY_BITS;
		return high | middle | (z & KEY_MASK);
	}

	/** The distance from {@code a} to {@code b}. */
	static double distance(double[] a, double[] b) {
		double dx = b[0] - a[0];
		double dy = b[1] - a[1];
		double dz = b[2] - a[2];
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/** The unit direction from {@code a} to {@code b}, which must differ. */
	static double[] unit(double[] a, double[] b) {
		double length = distance(a, b);
		return new double[] {(b[0] - a[0]) / length, (b[1] - a[1]) / length, (b[2] - a[2]) / length};
	}

	/** The deviation in degrees between the directions a → b and b → c, both of non-zero length. */
	static double bendDegrees(double[] a, double[] b, double[] c) {
		double ux = b[0] - a[0];
		double uy = b[1] - a[1];
		double uz = b[2] - a[2];
		double vx = c[0] - b[0];
		double vy = c[1] - b[1];
		double vz = c[2] - b[2];
		double norms = Math.sqrt((ux * ux + uy * uy + uz * uz) * (vx * vx + vy * vy + vz * vz));
		return Math.toDegrees(Math.acos(Math.clamp((ux * vx + uy * vy + uz * vz) / norms, -1, 1)));
	}

	/** The angle in degrees between two unit vectors. */
	static double angleDegrees(double[] d, double[] o) {
		return Math.toDegrees(Math.acos(Math.clamp(d[0] * o[0] + d[1] * o[1] + d[2] * o[2], -1, 1)));
	}

	/** The 26 neighbour directions, x slowest and z fastest, each from -1 to 1. */
	private static double[][] directions() {
		double[][] out = new double[CUBE - 1][];
		int i = 0;
		for (int code = 0; code < CUBE; code++) {
			if (code == CUBE / 2) {
				// The voxel itself.
				continue;
			}
			int dx = code / (SIDE * SIDE) - 1;
			int dy = code / SIDE % SIDE - 1;
			int dz = code % SIDE - 1;
			double n = Math.sqrt(dx * dx + dy * dy + dz * dz);
			out[i++] = new double[] {dx / n, dy / n, dz / n};
		}
		return out;
	}
}
