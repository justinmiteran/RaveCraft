package com.ravecraft.mod.core.acoustics.reverb;

import com.ravecraft.mod.core.acoustics.occlusion.OcclusionSettings;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * The server's reverb model: how far the room-sizing rays travel, the longest tail and the loudest wet signal, the
 * level lost between two rooms, the long-distance echo, and the reverb of each fluid. The media list keeps file
 * order and is immutable.
 */
public record ReverbSettings(boolean enabled, double rayLength, double maxDecaySeconds, double maxWetGain,
		double sameSpaceLossDb, EchoSettings echo, List<FluidReverb> media) {
	public static final int MAX_MEDIA = 16;

	public static final ReverbSettings DEFAULT = new ReverbSettings(true, 64, 6, 0.32, 6, EchoSettings.DEFAULT,
			List.of(FluidReverb.WATER, FluidReverb.LAVA));

	private static final Pattern NAME = Pattern.compile("[a-z0-9_]{1," + OcclusionSettings.MAX_NAME + "}");

	public ReverbSettings {
		media = List.copyOf(media);
	}

	/** The reverb of the fluid called {@code medium}, or null if there is none. */
	public @Nullable FluidReverb fluid(String medium) {
		for (FluidReverb f : media) {
			if (f.medium().equals(medium)) {
				return f;
			}
		}
		return null;
	}

	/** The same settings with every value brought into its bounds; {@code warn} hears about each change. */
	public ReverbSettings clamped(Consumer<String> warn) {
		double minDistance = bound("reverb.echo.minDistance", echo.minDistance(), 10, 35, warn);
		EchoSettings e = new EchoSettings(minDistance,
				bound("reverb.echo.maxDistance", echo.maxDistance(), minDistance, 35, warn),
				(int) bound("reverb.echo.minRays", echo.minRays(), 1, 16, warn),
				bound("reverb.echo.maxAbsorption", echo.maxAbsorption(), 0, 1, warn),
				bound("reverb.echo.gain", echo.gain(), 0, 1, warn),
				bound("reverb.echo.feedback", echo.feedback(), 0, 0.5, warn),
				bound("reverb.echo.damping", echo.damping(), 0, 0.99, warn));

		List<FluidReverb> kept = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (FluidReverb f : media) {
			String name = f.medium();
			if (name == null || !NAME.matcher(name).matches()) {
				warn.accept("reverb.media entry \"" + name + "\" is not a valid name ([a-z0-9_], 1 to "
						+ OcclusionSettings.MAX_NAME + " characters), dropped");
				continue;
			}
			if (!seen.add(name)) {
				warn.accept("reverb.media." + name + " is defined twice, keeping the first");
				continue;
			}
			String p = "reverb.media." + name + ".";
			kept.add(new FluidReverb(name,
					bound(p + "decaySeconds", f.decaySeconds(), 0.1, 20, warn),
					bound(p + "density", f.density(), 0, 1, warn),
					bound(p + "diffusion", f.diffusion(), 0, 1, warn),
					bound(p + "gain", f.gain(), 0, 1, warn),
					bound(p + "decayHfRatio", f.decayHfRatio(), 0.1, 2, warn),
					f.chorus(),
					bound(p + "chorusRateHz", f.chorusRateHz(), 0.05, 5, warn),
					bound(p + "chorusDepthMs", f.chorusDepthMs(), 0, 10, warn)));
		}
		if (kept.size() > MAX_MEDIA) {
			warn.accept("reverb.media has " + kept.size() + " entries, keeping the first " + MAX_MEDIA);
			kept = new ArrayList<>(kept.subList(0, MAX_MEDIA));
		}

		return new ReverbSettings(enabled,
				bound("reverb.rayLength", rayLength, 16, 128, warn),
				bound("reverb.maxDecaySeconds", maxDecaySeconds, 0.5, 20, warn),
				bound("reverb.maxWetGain", maxWetGain, 0, 1, warn),
				bound("reverb.sameSpaceLossDb", sameSpaceLossDb, 0, 60, warn),
				e, kept);
	}

	private static double bound(String name, double value, double min, double max, Consumer<String> warn) {
		double bounded = Double.isNaN(value) ? min : Math.clamp(value, min, max);
		if (bounded != value) {
			warn.accept(name + " = " + value + " is outside [" + min + ", " + max + "], using " + bounded);
		}
		return bounded;
	}
}
