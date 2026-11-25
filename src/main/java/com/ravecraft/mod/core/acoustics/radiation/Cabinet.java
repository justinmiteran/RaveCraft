package com.ravecraft.mod.core.acoustics.radiation;

import java.util.List;

/**
 * The box of a speaker and the drivers it holds: all the engine needs to compute how the speaker radiates.
 *
 * @param width its width, in metres (a block is one metre)
 * @param height its height, in metres
 * @param depth its depth, in metres
 * @param drivers its drivers
 */
public record Cabinet(double width, double height, double depth, List<Driver> drivers) {
	private static final double SPHERE_VOLUME = 4 * Math.PI / 3;

	/** Copies the list. */
	public Cabinet {
		drivers = List.copyOf(drivers);
	}

	/**
	 * The radius of the sphere of the same volume, which stands for the box in the calculation.
	 *
	 * @return the radius, in metres
	 */
	public double sphereRadius() {
		return Math.cbrt(width * height * depth / SPHERE_VOLUME);
	}
}
