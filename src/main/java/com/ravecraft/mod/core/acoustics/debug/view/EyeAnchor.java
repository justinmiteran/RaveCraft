package com.ravecraft.mod.core.acoustics.debug.view;

/**
 * Where to draw an end that sits at the listener's eye. A line from the eye is seen end-on in first person, a dot at
 * best, and flickers against the near plane; dropped below the eye, it shows as a line coming up from the bottom of
 * the screen. Seen from afar (third person, another player), the end stays where it is.
 */
public final class EyeAnchor {
	/** How far below the eye a near end is drawn, in blocks. */
	public static final double DROP = 0.6;
	/** Within this distance of the camera, an end is dropped. */
	public static final double NEAR = 1.5;

	private EyeAnchor() {
	}

	/** Where to draw {@code point}: {@link #DROP} below it when it is within {@link #NEAR} of the camera. */
	public static Vec of(Vec point, Vec camera) {
		double dx = point.x() - camera.x();
		double dy = point.y() - camera.y();
		double dz = point.z() - camera.z();
		if (dx * dx + dy * dy + dz * dz >= NEAR * NEAR) {
			return point;
		}
		return new Vec(point.x(), point.y() - DROP, point.z());
	}
}
