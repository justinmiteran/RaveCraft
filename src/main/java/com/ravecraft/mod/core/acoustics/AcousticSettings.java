package com.ravecraft.mod.core.acoustics;

import java.util.function.Consumer;

/**
 * The server's acoustic model, shared by every player so they all hear by the same rules: distance law exponent,
 * share of the range used to fade out, air absorption of the highs, the level below which a device is no longer
 * heard, the longest range allowed, and the portable speaker profile.
 */
public record AcousticSettings(double rolloff, double fadeFraction, double airAbsorptionDbPerBlock,
		double audibilityThresholdDb, double maxRange, DeviceProfile portable) {
	public static final AcousticSettings DEFAULT = new AcousticSettings(1.0, 0.2, 0.05, 68, 256, DeviceProfile.PORTABLE);

	/** The same settings with every value brought into its bounds; {@code warn} hears about each change. */
	public AcousticSettings clamped(Consumer<String> warn) {
		DeviceProfile p = portable;
		return new AcousticSettings(
				bound("rolloff", rolloff, 0.25, 3, warn),
				bound("fadeFraction", fadeFraction, 0.05, 0.5, warn),
				bound("airAbsorptionDbPerBlock", airAbsorptionDbPerBlock, 0, 1, warn),
				bound("audibilityThresholdDb", audibilityThresholdDb, 30, 100, warn),
				bound("maxRange", maxRange, 16, 512, warn),
				new DeviceProfile(
						bound("profiles.portable.powerWatts", p.powerWatts(), 0.1, 100_000, warn),
						bound("profiles.portable.sensitivityDb", p.sensitivityDb(), 70, 115, warn),
						bound("profiles.portable.bassCutDb", p.bassCutDb(), -24, 0, warn),
						bound("profiles.portable.bassCutHz", p.bassCutHz(), 40, 500, warn),
						p.directivity()));
	}

	private static double bound(String name, double value, double min, double max, Consumer<String> warn) {
		double bounded = Double.isNaN(value) ? min : Math.clamp(value, min, max);
		if (bounded != value) {
			warn.accept(name + " = " + value + " is outside [" + min + ", " + max + "], using " + bounded);
		}
		return bounded;
	}
}
