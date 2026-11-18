package com.ravecraft.mod.core.acoustics;

import com.ravecraft.mod.core.acoustics.occlusion.OcclusionSettings;
import java.util.function.Consumer;

/**
 * The server's acoustic model, shared by every player so they all hear by the same rules: distance law exponent,
 * share of the range used to fade out, air absorption of the highs, the level below which a device is no longer
 * heard, the longest range allowed, the portable speaker profile, and the occlusion model (which also holds the air
 * absorption of the highs).
 */
public record AcousticSettings(double rolloff, double fadeFraction, double audibilityThresholdDb, double maxRange,
		DeviceProfile portable, OcclusionSettings occlusion) {
	public static final AcousticSettings DEFAULT =
			new AcousticSettings(1.0, 0.2, 68, 256, DeviceProfile.PORTABLE, OcclusionSettings.DEFAULT);

	/** How much treble the air absorbs, in dB per block: the extra loss of the {@code air} medium. */
	public double airAbsorptionDbPerBlock() {
		return occlusion.air().highLossDbPerBlock();
	}

	/** The same settings with every value brought into its bounds; {@code warn} hears about each change. */
	public AcousticSettings clamped(Consumer<String> warn) {
		DeviceProfile p = portable;
		return new AcousticSettings(
				bound("rolloff", rolloff, 0.25, 3, warn),
				bound("fadeFraction", fadeFraction, 0.05, 0.5, warn),
				bound("audibilityThresholdDb", audibilityThresholdDb, 30, 100, warn),
				bound("maxRange", maxRange, 16, 512, warn),
				new DeviceProfile(
						bound("profiles.portable.powerWatts", p.powerWatts(), 0.1, 100_000, warn),
						bound("profiles.portable.sensitivityDb", p.sensitivityDb(), 70, 115, warn),
						bound("profiles.portable.bassCutDb", p.bassCutDb(), -24, 0, warn),
						bound("profiles.portable.bassCutHz", p.bassCutHz(), 40, 500, warn),
						p.directivity()),
				occlusion.clamped(warn));
	}

	private static double bound(String name, double value, double min, double max, Consumer<String> warn) {
		double bounded = Double.isNaN(value) ? min : Math.clamp(value, min, max);
		if (bounded != value) {
			warn.accept(name + " = " + value + " is outside [" + min + ", " + max + "], using " + bounded);
		}
		return bounded;
	}
}
