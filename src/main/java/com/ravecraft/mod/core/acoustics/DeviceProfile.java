package com.ravecraft.mod.core.acoustics;

/**
 * The acoustic character of a sound device, from its data sheet: amplifier power, sensitivity (dB SPL at 1 W and
 * 1 m), and its bass shelf. How far it carries follows from these and the server's hearing threshold.
 */
public record DeviceProfile(double powerWatts, double sensitivityDb, double bassCutDb, double bassCutHz) {
	/** The portable speaker: 20 W at 85 dB (about 32 blocks), weak bass. */
	public static final DeviceProfile PORTABLE = new DeviceProfile(20, 85, -6, 150);
	/** The party speaker: 120 W at 87 dB (about 97 blocks on axis). */
	public static final DeviceProfile PARTY = new DeviceProfile(120, 87, -2, 80);
}
