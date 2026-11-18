package com.ravecraft.mod.core.acoustics.reverb;

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

/** The debug screen (F3) lines describing the listener's space: what was measured and the reverb it gives. */
public final class ReverbDebug {
	private static final String TITLE = "RaveCraft reverb: ";

	private ReverbDebug() {
	}

	/**
	 * The lines for the listener's space: {@code fluid} names the fluid around the head when its preset applies;
	 * otherwise {@code shape} is the last measure (null while the first one runs).
	 */
	public static List<String> lines(boolean enabled, @Nullable RoomShape shape, ReverbParams params, Echo echo,
			@Nullable String fluid) {
		if (!enabled) {
			return List.of(TITLE + "off");
		}
		String reverb = format("  decay %.2f s | diffusion %.2f | reflections %.2f | gain %.2f", params.decayTime(),
				params.diffusion(), params.reflectionsGain(), params.gain());
		if (fluid != null) {
			return List.of(TITLE + "in " + fluid + " (preset)", reverb);
		}
		if (shape == null) {
			return List.of(TITLE + "measuring...");
		}
		String measure = format("open %d%% | free path %.1f | absorption %.2f | irregular %.2f",
				Math.round(shape.openness() * 100), shape.meanFreePath(), shape.absorption(), shape.irregularity());
		String echoLine = echo.gain() <= 0 ? "  echo: none"
				: format("  echo: %.2f s, gain %.2f", echo.delaySeconds(), echo.gain());
		return List.of(TITLE + measure, reverb, echoLine);
	}

	private static String format(String pattern, Object... args) {
		return String.format(Locale.ROOT, pattern, args);
	}
}
