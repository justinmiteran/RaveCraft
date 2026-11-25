package com.ravecraft.mod.core.acoustics;

/**
 * Realistic sound propagation: inverse distance law (−6 dB per doubling with rolloff 1), a range derived from the
 * device's power and sensitivity, a smooth fade over the end of that range, the gain of the listener's direction, and
 * high-frequency absorption by the air.
 */
public final class Propagation {
	/** Extra blocks the server relays beyond the audible range, for movement between two chunks. */
	public static final double RELAY_MARGIN = 8.0;
	/** The air never removes more than this share of the highs (−26 dB). */
	private static final double MIN_HIGH_GAIN = 0.05;

	private Propagation() {
	}

	/**
	 * Sound pressure level 1 m in front of the device at full volume (dB SPL).
	 *
	 * @param profile the device
	 * @return the level, in dB SPL
	 */
	public static double levelAt1m(DeviceProfile profile) {
		return profile.sensitivityDb() + Decibels.fromPower(profile.powerWatts());
	}

	/**
	 * How far the device carries at {@code volume} (0 to 1): the distance where its level, falling by the distance
	 * law, reaches the hearing threshold. Never more than the server's {@code maxRange}.
	 *
	 * @param settings the acoustic settings
	 * @param profile the device
	 * @param volume the volume, 0 to 1
	 * @return the range, in blocks
	 */
	public static double range(AcousticSettings settings, DeviceProfile profile, double volume) {
		if (volume <= 0) {
			return 0;
		}
		double headroomDb = levelAt1m(profile) + Decibels.fromAmplitude(volume) - settings.audibilityThresholdDb();
		return Math.min(settings.maxRange(), Math.pow(Decibels.DECADE, headroomDb / (Decibels.AMPLITUDE_DB * settings.rolloff())));
	}

	/**
	 * {@code 1 / d^rolloff}, full gain inside one block.
	 *
	 * @param rolloff the distance law's exponent
	 * @param distance the distance, in blocks
	 * @return the gain
	 */
	public static double distanceGain(double rolloff, double distance) {
		return 1 / Math.pow(Math.max(distance, 1), rolloff);
	}

	/**
	 * 1 until {@code (1 - fadeFraction) × range}, then a half cosine down to exactly 0 at {@code range}.
	 *
	 * @param distance the distance, in blocks
	 * @param range the range, in blocks
	 * @param fadeFraction the share of the range that fades
	 * @return the fade gain, 0 to 1
	 */
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

	/**
	 * Linear gain at the listener, before the player's own volume settings.
	 *
	 * @param settings the acoustic settings
	 * @param profile the device
	 * @param volume the volume, 0 to 1
	 * @param distance the distance, in blocks
	 * @param directionGain the gain of the listener's direction, 1 on the device's axis
	 * @return the linear gain
	 */
	public static double gain(AcousticSettings settings, DeviceProfile profile, double volume, double distance,
			double directionGain) {
		if (volume <= 0) {
			return 0;
		}
		return volume * distanceGain(settings.rolloff(), distance)
				* fade(distance, range(settings, profile, volume), settings.fadeFraction())
				* directionGain;
	}

	/**
	 * Linear gain of the highs after {@code distance} blocks of air.
	 *
	 * @param dbPerBlock the air absorption, in decibels per block
	 * @param distance the distance, in blocks
	 * @return the linear gain of the highs
	 */
	public static double highFrequencyGain(double dbPerBlock, double distance) {
		return Math.max(MIN_HIGH_GAIN, Decibels.toAmplitude(-dbPerBlock * distance));
	}

	/**
	 * Players within this distance of the device receive its stream; 0 when silent.
	 *
	 * @param settings the acoustic settings
	 * @param profile the device
	 * @param volume the volume, 0 to 1
	 * @return the radius, in blocks
	 */
	public static double relayRadius(AcousticSettings settings, DeviceProfile profile, double volume) {
		return volume <= 0 ? 0 : range(settings, profile, volume) + RELAY_MARGIN;
	}
}
