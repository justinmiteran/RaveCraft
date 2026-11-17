package com.ravecraft.mod.core.acoustics;

/**
 * Realistic sound propagation: inverse distance law (−6 dB per doubling with rolloff 1), a smooth fade over the end
 * of the device range, directivity, and high-frequency absorption by the air.
 */
public final class Propagation {
	/** Extra blocks the server relays beyond the audible range, for movement between two chunks. */
	public static final double RELAY_MARGIN = 8.0;
	/** The air never removes more than this share of the highs (−26 dB). */
	private static final double MIN_HIGH_GAIN = 0.05;

	private Propagation() {
	}

	/** How far the device carries at {@code volume} (0 to 1). */
	public static double range(DeviceProfile profile, double volume) {
		return profile.range() * volume;
	}

	/** {@code 1 / d^rolloff}, full gain inside one block. */
	public static double distanceGain(double rolloff, double distance) {
		return 1 / Math.pow(Math.max(distance, 1), rolloff);
	}

	/** 1 until {@code (1 - fadeFraction) × range}, then a half cosine down to exactly 0 at {@code range}. */
	public static double fade(double distance, double range, double fadeFraction) {
		if (range <= 0 || distance >= range) {
			return 0;
		}
		double start = (1 - fadeFraction) * range;
		if (distance <= start) {
			return 1;
		}
		double t = (distance - start) / (fadeFraction * range);
		return 0.5 * (1 + Math.cos(Math.PI * t));
	}

	/** Linear gain at the listener, before the player's own volume settings. */
	public static double gain(double rolloff, double fadeFraction, DeviceProfile profile, double volume, double distance,
			double cosAngle) {
		if (volume <= 0) {
			return 0;
		}
		return volume * distanceGain(rolloff, distance) * fade(distance, range(profile, volume), fadeFraction)
				* profile.directivity().gain(cosAngle);
	}

	/** Linear gain of the highs after {@code distance} blocks of air. */
	public static double highFrequencyGain(double dbPerBlock, double distance) {
		return Math.max(MIN_HIGH_GAIN, Math.pow(10, -dbPerBlock * distance / 20));
	}

	/** Players within this distance of the device receive its stream; 0 when silent. */
	public static double relayRadius(DeviceProfile profile, double volume) {
		return volume <= 0 ? 0 : range(profile, volume) + RELAY_MARGIN;
	}
}
