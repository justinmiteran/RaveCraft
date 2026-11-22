package com.ravecraft.mod.core.acoustics.reverb;

import com.ravecraft.mod.core.acoustics.Bound;
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

	private static final Bound ECHO_DISTANCE = new Bound(10, 35);
	private static final Bound ECHO_MIN_RAYS = new Bound(1, 16);
	private static final Bound ECHO_FEEDBACK = new Bound(0, 0.5);
	private static final Bound ECHO_DAMPING = new Bound(0, 0.99);
	private static final Bound DECAY_SECONDS = new Bound(0.1, 20);
	private static final Bound DECAY_HF_RATIO = new Bound(0.1, 2);
	private static final Bound CHORUS_RATE_HZ = new Bound(0.05, 5);
	private static final Bound CHORUS_DEPTH_MS = new Bound(0, 10);
	private static final Bound RAY_LENGTH = new Bound(16, 128);
	private static final Bound MAX_DECAY_SECONDS = new Bound(0.5, 20);
	private static final Bound SAME_SPACE_LOSS_DB = new Bound(0, 60);

	private static final Pattern NAME = Pattern.compile("[a-z0-9_]{1," + OcclusionSettings.MAX_NAME + "}");

	/** Copies {@code media}, so the settings never change. */
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
		EchoSettings e = clampedEcho(warn);
		List<FluidReverb> kept = clampedMedia(warn);
		return new ReverbSettings(enabled,
				RAY_LENGTH.apply("reverb.rayLength", rayLength, warn),
				MAX_DECAY_SECONDS.apply("reverb.maxDecaySeconds", maxDecaySeconds, warn),
				Bound.UNIT.apply("reverb.maxWetGain", maxWetGain, warn),
				SAME_SPACE_LOSS_DB.apply("reverb.sameSpaceLossDb", sameSpaceLossDb, warn),
				e, kept);
	}

	private EchoSettings clampedEcho(Consumer<String> warn) {
		double minDistance = ECHO_DISTANCE.apply("reverb.echo.minDistance", echo.minDistance(), warn);
		return new EchoSettings(minDistance,
				new Bound(minDistance, ECHO_DISTANCE.max()).apply("reverb.echo.maxDistance", echo.maxDistance(), warn),
				(int) ECHO_MIN_RAYS.apply("reverb.echo.minRays", echo.minRays(), warn),
				Bound.UNIT.apply("reverb.echo.maxAbsorption", echo.maxAbsorption(), warn),
				Bound.UNIT.apply("reverb.echo.gain", echo.gain(), warn),
				ECHO_FEEDBACK.apply("reverb.echo.feedback", echo.feedback(), warn),
				ECHO_DAMPING.apply("reverb.echo.damping", echo.damping(), warn));
	}

	/** The fluids with valid, distinct names, their values in bounds, up to {@link #MAX_MEDIA}. */
	private List<FluidReverb> clampedMedia(Consumer<String> warn) {
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
					DECAY_SECONDS.apply(p + "decaySeconds", f.decaySeconds(), warn),
					Bound.UNIT.apply(p + "density", f.density(), warn),
					Bound.UNIT.apply(p + "diffusion", f.diffusion(), warn),
					Bound.UNIT.apply(p + "gain", f.gain(), warn),
					DECAY_HF_RATIO.apply(p + "decayHfRatio", f.decayHfRatio(), warn),
					f.chorus(),
					CHORUS_RATE_HZ.apply(p + "chorusRateHz", f.chorusRateHz(), warn),
					CHORUS_DEPTH_MS.apply(p + "chorusDepthMs", f.chorusDepthMs(), warn)));
		}
		if (kept.size() > MAX_MEDIA) {
			warn.accept("reverb.media has " + kept.size() + " entries, keeping the first " + MAX_MEDIA);
			kept = new ArrayList<>(kept.subList(0, MAX_MEDIA));
		}
		return kept;
	}
}
