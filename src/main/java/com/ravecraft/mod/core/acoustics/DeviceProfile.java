package com.ravecraft.mod.core.acoustics;

/**
 * The acoustic character of a sound device: how far it carries at full volume (blocks), its bass shelf and how it
 * radiates.
 */
public record DeviceProfile(double range, double bassCutDb, double bassCutHz, Directivity directivity) {
	/** The portable speaker: 32 blocks, weak bass, omnidirectional. */
	public static final DeviceProfile PORTABLE = new DeviceProfile(32, -6, 150, Directivity.OMNI);
}
