package com.ravecraft.mod.core.acoustics.debug.view;

import org.jspecify.annotations.Nullable;

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

	public static Vec of(Vec point, Vec camera) {
		double dx = point.x() - camera.x();
		double dy = point.y() - camera.y();
		double dz = point.z() - camera.z();
		if (dx * dx + dy * dy + dz * dz >= NEAR * NEAR) {
			return point;
		}
		return new Vec(point.x(), point.y() - DROP, point.z());
	}

	/**
	 * The part of the segment a → b at least {@code near} in front of the camera (along the unit vector
	 * {@code forward}), or null when none is. A line is widened on screen, and an end on or behind the camera's plane
	 * makes the whole line vanish or flicker.
	 */
	public static Vec @Nullable [] clip(Vec a, Vec b, Vec camera, Vec forward, double near) {
		double da = depth(a, camera, forward);
		double db = depth(b, camera, forward);
		if (da < near && db < near) {
			return null;
		}
		if (da < near) {
			return new Vec[] {lerp(a, b, (near - da) / (db - da)), b};
		}
		if (db < near) {
			return new Vec[] {a, lerp(a, b, (da - near) / (da - db))};
		}
		return new Vec[] {a, b};
	}

	private static double depth(Vec p, Vec camera, Vec forward) {
		return (p.x() - camera.x()) * forward.x() + (p.y() - camera.y()) * forward.y()
				+ (p.z() - camera.z()) * forward.z();
	}

	private static Vec lerp(Vec a, Vec b, double t) {
		return new Vec(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t, a.z() + (b.z() - a.z()) * t);
	}
}
