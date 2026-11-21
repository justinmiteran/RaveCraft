package com.ravecraft.mod.core.acoustics;

/**
 * How a device radiates around its axis: full gain inside {@code halfAngleDeg}, then a smooth (half-cosine in dB)
 * fall to {@code rearGainDb} straight behind. {@link #OMNI} radiates equally everywhere.
 */
public record Directivity(double halfAngleDeg, double rearGainDb) {
	public static final Directivity OMNI = new Directivity(180, 0);

	private static final double REAR_DEG = 180;

	/** Linear gain for a listener at {@code cosAngle} (cosine of the angle between the axis and the listener). */
	public double gain(double cosAngle) {
		double angle = Math.toDegrees(Math.acos(Math.clamp(cosAngle, -1.0, 1.0)));
		if (angle <= halfAngleDeg) {
			return 1;
		}
		double t = (angle - halfAngleDeg) / (REAR_DEG - halfAngleDeg);
		double db = rearGainDb * (1 - Math.cos(Math.PI * t)) / 2;
		return Decibels.toAmplitude(db);
	}
}
