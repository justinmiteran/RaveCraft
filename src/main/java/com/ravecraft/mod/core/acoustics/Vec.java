package com.ravecraft.mod.core.acoustics;

/** A point in the world, in blocks. */
public record Vec(double x, double y, double z) {
	/** This point moved by {@code length} along the unit direction {@code d}. */
	public Vec along(double[] d, double length) {
		return new Vec(x + d[0] * length, y + d[1] * length, z + d[2] * length);
	}
}
