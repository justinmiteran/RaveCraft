package com.ravecraft.mod.core.acoustics.occlusion;

/**
 * Smooths an occlusion loss so that a door opening or a wall placed does not click: the value moves linearly toward
 * its target and gets there {@code rampMs} after the target changed. The first value is taken as is. An infinite
 * target (mute) ramps to {@value #MUTE_DB} dB then becomes infinite; leaving mute ramps down from {@value #MUTE_DB}.
 * Not thread-safe.
 */
public final class LossSmoother {
	/** The finite loss a ramp to or from mute passes through. */
	public static final double MUTE_DB = 120;
	private static final double MIN_RAMP_MS = 1e-9;

	private final double rampMs;
	private boolean started;
	private double value;
	private double target;
	/** dB per ms toward the current target. */
	private double rate;

	/**
	 * Creates a smoother.
	 *
	 * @param rampMs how long a change of target takes to reach
	 */
	public LossSmoother(double rampMs) {
		this.rampMs = Math.max(rampMs, MIN_RAMP_MS);
	}

	/** The smoothed loss after {@code dtMs} more milliseconds toward {@code target}. */
	public double step(double target, double dtMs) {
		if (Double.isNaN(target)) {
			target = 0;
		}
		if (!started) {
			started = true;
			value = target;
			this.target = target;
			return value;
		}
		double goal = Math.min(target, MUTE_DB);
		if (target != this.target) {
			this.target = target;
			if (Double.isInfinite(value)) {
				value = MUTE_DB;
			}
			rate = Math.abs(goal - value) / rampMs;
		}
		if (Double.isInfinite(value)) {
			return value;
		}
		double delta = goal - value;
		double move = rate * Math.max(0, dtMs);
		if (Math.abs(delta) <= move) {
			value = Double.isInfinite(target) ? Double.POSITIVE_INFINITY : goal;
		} else {
			value += Math.signum(delta) * move;
		}
		return value;
	}

	/** Forgets the current value: the next {@link #step} jumps to its target. */
	public void reset() {
		started = false;
	}
}
