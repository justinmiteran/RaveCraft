package com.ravecraft.mod.core.acoustics;

import java.util.function.Consumer;

/**
 * The allowed range of one setting read from a file. A value outside it is brought back to the nearest end, and NaN
 * to {@code min}, with a warning naming the setting.
 *
 * @param min the smallest allowed value
 * @param max the largest allowed value
 */
public record Bound(double min, double max) {
	/** From 0 to 1: shares, gains and absorptions. */
	public static final Bound UNIT = new Bound(0, 1);

	/**
	 * Brings {@code value} into the range.
	 *
	 * @param name the setting's path in the file, for the warning
	 * @param value the value read
	 * @param warn hears about the change, if there is one
	 * @return the value in range
	 */
	public double apply(String name, double value, Consumer<String> warn) {
		double bounded = Double.isNaN(value) ? min : Math.clamp(value, min, max);
		if (bounded != value) {
			warn.accept(name + " = " + value + " is outside [" + min + ", " + max + "], using " + bounded);
		}
		return bounded;
	}
}
