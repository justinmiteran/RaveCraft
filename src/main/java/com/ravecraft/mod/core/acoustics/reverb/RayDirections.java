package com.ravecraft.mod.core.acoustics.reverb;

/** Evenly spread directions over the sphere. */
public final class RayDirections {
	private static final double GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));

	private RayDirections() {
	}

	/** {@code n} unit vectors on a Fibonacci spiral, always the same for the same {@code n}. */
	public static double[][] sphere(int n) {
		double[][] out = new double[Math.max(0, n)][];
		for (int i = 0; i < out.length; i++) {
			double y = 1 - 2 * (i + 0.5) / n;
			double r = Math.sqrt(Math.max(0, 1 - y * y));
			double angle = GOLDEN_ANGLE * i;
			out[i] = new double[] {Math.cos(angle) * r, y, Math.sin(angle) * r};
		}
		return out;
	}
}
