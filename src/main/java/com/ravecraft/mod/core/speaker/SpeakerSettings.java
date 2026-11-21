package com.ravecraft.mod.core.speaker;

/**
 * Immutable settings of a portable speaker: power state and output volume.
 * The volume is always clamped into [{@link #MIN_VOLUME}, {@link #MAX_VOLUME}].
 */
public record SpeakerSettings(boolean powered, int volume) {
	public static final int MIN_VOLUME = 0;
	public static final int MAX_VOLUME = 100;
	public static final int DEFAULT_VOLUME = 70;
	public static final SpeakerSettings DEFAULT = new SpeakerSettings(false, DEFAULT_VOLUME);

	/** Clamps the volume to its range. */
	public SpeakerSettings {
		volume = Math.clamp(volume, MIN_VOLUME, MAX_VOLUME);
	}

	/** Returns the same settings with the power state flipped and the volume unchanged. */
	public SpeakerSettings togglePower() {
		return new SpeakerSettings(!powered, volume);
	}

	/** Returns the same settings with the volume replaced (clamped) and the power state unchanged. */
	public SpeakerSettings withVolume(int volume) {
		return new SpeakerSettings(powered, volume);
	}
}
