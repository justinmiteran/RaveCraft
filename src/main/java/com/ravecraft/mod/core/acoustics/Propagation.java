package com.ravecraft.mod.core.acoustics;

/**
 * Realistic sound propagation: inverse distance law (−6 dB per doubling with rolloff 1), a range derived from the
 * device's power and sensitivity, a smooth fade over the end of that range, directivity, and high-frequency
 * absorption by the air.
 */
public final class Propagation {
	/** Extra blocks the server relays beyond the audible range, for movement between two chunks. */
	public static final double RELAY_MARGIN = 8.0;
	/** The air never removes more than this share of the highs (−26 dB). */
	private static final double MIN_HIGH_GAIN = 0.05;

	private Propagation() {
	}

	/** Sound pressure level 1 m in front of the device at full volume (dB SPL). */
	public static double levelAt1m(DeviceProfile profile) {
		return profile.sensitivityDb() + Decibels.fromPower(profile.powerWatts());
	}

	/**
	 * How far the device carries at {@code volume} (0 to 1): the distance where its level, falling by the distance
	 * law, reaches the hearing threshold. Never more than the server's {@code maxRange}.
	 */
	public static double range(AcousticSettings settings, DeviceProfile profile, double volume) {
		if (volume <= 0) {
			return 0;
		}
		double headroomDb = levelAt1m(profile) + Decibels.fromAmplitude(volume) - settings.audibilityThresholdDb();
		return Math.min(settings.maxRange(), Math.pow(Decibels.DECADE, headroomDb / (Decibels.AMPLITUDE_DB * settings.rolloff())));
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
		return (1 + Math.cos(Math.PI * t)) / 2;
	}

	/** Linear gain at the listener, before the player's own volume settings. */
	public static double gain(AcousticSettings settings, DeviceProfile profile, double volume, double distance,
			double cosAngle) {
		return gain(settings, profile, profile.directivity(), volume, distance, cosAngle);
	}

	/**
	 * Linear gain at the listener with {@code directivity} in place of the profile's own (a speaker on the ground
	 * radiates in every direction, whatever its kind).
	 */
	public static double gain(AcousticSettings settings, DeviceProfile profile, Directivity directivity, double volume,
			double distance, double cosAngle) {
		if (volume <= 0) {
			return 0;
		}
		return volume * distanceGain(settings.rolloff(), distance)
				* fade(distance, range(settings, profile, volume), settings.fadeFraction())
				* directivity.gain(cosAngle);
	}

	/** Linear gain of the highs after {@code distance} blocks of air. */
	public static double highFrequencyGain(double dbPerBlock, double distance) {
		return Math.max(MIN_HIGH_GAIN, Decibels.toAmplitude(-dbPerBlock * distance));
	}

	/** Players within this distance of the device receive its stream; 0 when silent. */
	public static double relayRadius(AcousticSettings settings, DeviceProfile profile, double volume) {
		return volume <= 0 ? 0 : range(settings, profile, volume) + RELAY_MARGIN;
	}
}
