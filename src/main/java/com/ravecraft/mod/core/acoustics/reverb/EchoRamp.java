package com.ravecraft.mod.core.acoustics.reverb;

/**
 * The echo as actually applied to the effect: its gain moves linearly (1 per {@link #RAMP_MS}) toward the target,
 * and its delay changes only while the gain is 0, so the echo's tap never jumps audibly. Not thread-safe.
 */
public final class EchoRamp {
	public static final double RAMP_MS = 100;
	/** Delay changes smaller than this keep the applied delay. */
	public static final double DELAY_TOLERANCE_SECONDS = 0.005;

	private double delay;
	private double gain;

	/**
	 * The delay applied now.
	 *
	 * @return the delay, in seconds
	 */
	public double delaySeconds() {
		return delay;
	}

	/**
	 * The gain applied now.
	 *
	 * @return the gain
	 */
	public double gain() {
		return gain;
	}

	/**
	 * Advances by {@code dtMs} toward {@code target}. {@link Echo#NONE} (or no gain) only fades the gain out.
	 *
	 * @param target the echo to reach
	 * @param dtMs the time elapsed, in milliseconds
	 */
	public void step(Echo target, double dtMs) {
		boolean silent = target.gain() <= 0 || target.delaySeconds() <= 0;
		double goal = silent ? 0 : target.gain();
		if (!silent && Math.abs(target.delaySeconds() - delay) >= DELAY_TOLERANCE_SECONDS) {
			if (gain <= 0) {
				delay = target.delaySeconds();
			} else {
				goal = 0;
			}
		}
		double max = Math.max(0, dtMs) / RAMP_MS;
		gain = goal > gain ? Math.min(goal, gain + max) : Math.max(goal, gain - max);
		if (!silent && gain <= 0 && Math.abs(target.delaySeconds() - delay) >= DELAY_TOLERANCE_SECONDS) {
			delay = target.delaySeconds();
		}
	}
}
