package com.ravecraft.mod.core.acoustics;

import com.ravecraft.mod.core.speaker.CarryPlace;

/**
 * The horizontal direction a speaker points, used to pick its {@link Directivity} gain. A placed speaker points along
 * its facing; a carried one points along the carrier's look (hand or inventory) or behind the carrier (back); a speaker
 * on the ground radiates in every direction.
 *
 * @param x horizontal x component of the axis (Minecraft convention)
 * @param z horizontal z component of the axis (Minecraft convention)
 * @param directed {@code false} when the device is omnidirectional on the ground
 */
public record SpeakerAxis(double x, double z, boolean directed) {
	/** Yaw offset that turns the body's forward direction to point behind the carrier. */
	private static final double BEHIND_YAW_DEG = 180;

	/** A speaker on the ground: radiates equally around itself. */
	public static final SpeakerAxis GROUND = new SpeakerAxis(0, 1, false);

	/**
	 * The axis of a placed speaker, from the horizontal step of its facing.
	 *
	 * @param stepX horizontal x step of the facing (-1, 0 or 1)
	 * @param stepZ horizontal z step of the facing (-1, 0 or 1)
	 * @return a directed axis along the facing
	 */
	public static SpeakerAxis block(int stepX, int stepZ) {
		return new SpeakerAxis(stepX, stepZ, true);
	}

	/**
	 * The axis of a carried speaker: the look direction in hand or inventory, the opposite of the body yaw on the back.
	 *
	 * @param place where the speaker is carried
	 * @param lookYawDeg the carrier's look yaw, in degrees
	 * @param bodyYawDeg the carrier's body yaw, in degrees
	 * @return a directed axis
	 */
	public static SpeakerAxis carried(CarryPlace place, float lookYawDeg, float bodyYawDeg) {
		double yaw = place == CarryPlace.BACK ? bodyYawDeg + BEHIND_YAW_DEG : lookYawDeg;
		double radians = Math.toRadians(yaw);
		return new SpeakerAxis(-Math.sin(radians), Math.cos(radians), true);
	}

	/**
	 * The radiation pattern to use for this axis.
	 *
	 * @param profile the device profile of the speaker
	 * @return the profile's directivity when directed, otherwise {@link Directivity#OMNI}
	 */
	public Directivity directivity(DeviceProfile profile) {
		return directed ? profile.directivity() : Directivity.OMNI;
	}
}
