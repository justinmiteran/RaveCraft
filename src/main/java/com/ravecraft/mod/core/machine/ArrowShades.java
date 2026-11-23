package com.ravecraft.mod.core.machine;

/**
 * The tones of a machine's progress arrow, derived from the input's colour. A tint can only darken a white sprite, so
 * the arrow is drawn as stacked white masks, each in one of these tones: a hue-shifted metallic ramp (cool in the
 * shadow, warm in the light, most saturated in the mid tones) and a soft diagonal sheen blended toward the light tone.
 * Grey inputs, white included, give the same cool steel.
 */
public final class ArrowShades {
	/** Number of layers, darkest first: shadow, lower rim, body, then the sheen at a quarter, half and 0.7. */
	public static final int LAYERS = 6;

	private static final int OPAQUE = 0xFF000000;
	private static final int BYTE = 0xFF;
	private static final double CHANNEL = 255.0;
	private static final int RED_SHIFT = 16;
	private static final int GREEN_SHIFT = 8;
	private static final int RAMP_STEPS = 6;
	private static final int SHADOW_TONE = 1;
	private static final int RIM_TONE = 2;
	private static final int BODY_TONE = 3;
	private static final int LIGHT_TONE = 4;
	private static final double[] SHEEN = {0.25, 0.5, 0.7};
	private static final double MIN_LIGHTNESS = 0.16;
	private static final double LIGHTNESS_SPAN = 0.77;
	private static final double HUE_DRIFT = 0.10;
	private static final double HALF = 0.5;
	private static final double GREY_SATURATION = 0.08;
	private static final double STEEL_HUE = 0.62;
	private static final double STEEL_SATURATION = 0.10;
	private static final double STEEL_SATURATION_FLOOR = 0.02;
	private static final double SATURATION_BASE = 0.75;
	private static final double SATURATION_PEAK = 0.45;
	private static final double ONE_THIRD = 1.0 / 3.0;
	private static final double TWO_THIRDS = 2.0 / 3.0;
	private static final double ONE_SIXTH = 1.0 / 6.0;
	private static final double SECTORS = 6.0;
	private static final double GREEN_SECTOR = 2.0;
	private static final double BLUE_SECTOR = 4.0;

	private ArrowShades() {
	}

	/**
	 * Returns the arrow's layer colours for an input colour.
	 *
	 * @param argb the input's colour (alpha ignored)
	 * @return {@link #LAYERS} opaque ARGB colours, darkest first
	 */
	public static int[] layers(int argb) {
		double[] hls = toHls((argb >> RED_SHIFT & BYTE) / CHANNEL, (argb >> GREEN_SHIFT & BYTE) / CHANNEL,
				(argb & BYTE) / CHANNEL);
		int[] body = tone(hls, BODY_TONE);
		int[] light = tone(hls, LIGHT_TONE);
		int[] out = new int[LAYERS];
		out[0] = pack(tone(hls, SHADOW_TONE));
		out[1] = pack(tone(hls, RIM_TONE));
		out[2] = pack(body);
		for (int i = 0; i < SHEEN.length; i++) {
			out[BODY_TONE + i] = pack(mix(body, light, SHEEN[i]));
		}
		return out;
	}

	/** Tone {@code step} of the 7-step ramp (0 darkest, 6 lightest) as 0-255 channels. */
	private static int[] tone(double[] hls, int step) {
		double k = step / (double) RAMP_STEPS;
		double lightness = MIN_LIGHTNESS + k * LIGHTNESS_SPAN;
		double hue;
		double saturation;
		if (hls[2] < GREY_SATURATION) {
			hue = STEEL_HUE;
			saturation = STEEL_SATURATION * (1 - k) + STEEL_SATURATION_FLOOR;
		} else {
			hue = hls[0] + (HALF - k) * HUE_DRIFT;
			saturation = Math.min(1.0, hls[2] * (SATURATION_BASE + HALF * (1 - Math.abs(k - SATURATION_PEAK) * 2)));
		}
		return toRgb(hue - Math.floor(hue), lightness, saturation);
	}

	private static int[] mix(int[] from, int[] to, double amount) {
		int[] out = new int[from.length];
		for (int c = 0; c < from.length; c++) {
			out[c] = (int) (from[c] * (1 - amount) + to[c] * amount);
		}
		return out;
	}

	private static int pack(int[] rgb) {
		return OPAQUE | rgb[0] << RED_SHIFT | rgb[1] << GREEN_SHIFT | rgb[2];
	}

	/** RGB (0-1) to hue, lightness, saturation (0-1), as Python's {@code colorsys.rgb_to_hls}. */
	private static double[] toHls(double r, double g, double b) {
		double max = Math.max(r, Math.max(g, b));
		double min = Math.min(r, Math.min(g, b));
		double lightness = (max + min) / 2.0;
		if (max == min) {
			return new double[] {0.0, lightness, 0.0};
		}
		double range = max - min;
		double saturation = lightness <= HALF ? range / (max + min) : range / (2.0 - max - min);
		double rc = (max - r) / range;
		double gc = (max - g) / range;
		double bc = (max - b) / range;
		double hue;
		if (r == max) {
			hue = bc - gc;
		} else if (g == max) {
			hue = GREEN_SECTOR + rc - bc;
		} else {
			hue = BLUE_SECTOR + gc - rc;
		}
		hue = hue / SECTORS;
		return new double[] {hue - Math.floor(hue), lightness, saturation};
	}

	/** Hue, lightness, saturation (0-1) to 0-255 channels, truncated, as Python's {@code colorsys.hls_to_rgb}. */
	private static int[] toRgb(double hue, double lightness, double saturation) {
		double m2 = lightness <= HALF ? lightness * (1.0 + saturation) : lightness + saturation - lightness * saturation;
		double m1 = 2.0 * lightness - m2;
		return new int[] {channel(m1, m2, hue + ONE_THIRD), channel(m1, m2, hue), channel(m1, m2, hue - ONE_THIRD)};
	}

	/** One 0-255 channel of {@link #toRgb}, its hue offset by a third for red and blue. */
	private static int channel(double m1, double m2, double hue) {
		double h = hue - Math.floor(hue);
		double value;
		if (h < ONE_SIXTH) {
			value = m1 + (m2 - m1) * h * SECTORS;
		} else if (h < HALF) {
			value = m2;
		} else if (h < TWO_THIRDS) {
			value = m1 + (m2 - m1) * (TWO_THIRDS - h) * SECTORS;
		} else {
			value = m1;
		}
		return (int) (value * CHANNEL);
	}
}
