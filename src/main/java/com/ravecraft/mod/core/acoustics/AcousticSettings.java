package com.ravecraft.mod.core.acoustics;

import com.ravecraft.mod.core.acoustics.occlusion.OcclusionSettings;
import com.ravecraft.mod.core.acoustics.reverb.ReverbSettings;
import java.util.function.Consumer;

/**
 * The server's acoustic model, shared by every player so they all hear by the same rules: distance law exponent,
 * share of the range used to fade out, air absorption of the highs, the level below which a device is no longer
 * heard, the longest range allowed, the portable speaker profile, the occlusion model (which also holds the air
 * absorption of the highs), and the reverb model.
 */
public record AcousticSettings(double rolloff, double fadeFraction, double audibilityThresholdDb, double maxRange,
		DeviceProfile portable, OcclusionSettings occlusion, ReverbSettings reverb) {
	public static final AcousticSettings DEFAULT =
			new AcousticSettings(1.0, 0.2, 68, 256, DeviceProfile.PORTABLE, OcclusionSettings.DEFAULT,
					ReverbSettings.DEFAULT);

	private static final Bound ROLLOFF = new Bound(0.25, 3);
	private static final Bound FADE_FRACTION = new Bound(0.05, 0.5);
	private static final Bound AUDIBILITY_DB = new Bound(30, 100);
	private static final Bound MAX_RANGE = new Bound(16, 512);
	private static final Bound POWER_WATTS = new Bound(0.1, 100_000);
	private static final Bound SENSITIVITY_DB = new Bound(70, 115);
	private static final Bound BASS_CUT_DB = new Bound(-24, 0);
	private static final Bound BASS_CUT_HZ = new Bound(40, 500);

	/** How much treble the air absorbs, in dB per block: the extra loss of the {@code air} medium. */
	public double airAbsorptionDbPerBlock() {
		return occlusion.air().highLossDbPerBlock();
	}

	/** The same settings with every value brought into its bounds; {@code warn} hears about each change. */
	public AcousticSettings clamped(Consumer<String> warn) {
		DeviceProfile p = portable;
		return new AcousticSettings(
				ROLLOFF.apply("rolloff", rolloff, warn),
				FADE_FRACTION.apply("fadeFraction", fadeFraction, warn),
				AUDIBILITY_DB.apply("audibilityThresholdDb", audibilityThresholdDb, warn),
				MAX_RANGE.apply("maxRange", maxRange, warn),
				new DeviceProfile(
						POWER_WATTS.apply("profiles.portable.powerWatts", p.powerWatts(), warn),
						SENSITIVITY_DB.apply("profiles.portable.sensitivityDb", p.sensitivityDb(), warn),
						BASS_CUT_DB.apply("profiles.portable.bassCutDb", p.bassCutDb(), warn),
						BASS_CUT_HZ.apply("profiles.portable.bassCutHz", p.bassCutHz(), warn),
						p.directivity()),
				occlusion.clamped(warn),
				reverb.clamped(warn));
	}
}
