package com.ravecraft.mod.core.acoustics.reverb;

/**
 * Moves a reverb linearly to its target in {@link #GLIDE_MS}: a new target starts a new glide from wherever the
 * reverb is, with no jump. Not thread-safe.
 */
public final class ParamGlide {
	public static final double GLIDE_MS = 1000;

	private ReverbParams start;
	private ReverbParams target;
	private ReverbParams current;
	private long startMs;

	/**
	 * Creates a glide resting on {@code initial}.
	 *
	 * @param initial the reverb to start from
	 */
	public ParamGlide(ReverbParams initial) {
		start = initial;
		target = initial;
		current = initial;
	}

	/**
	 * The reverb at {@code nowMs} on the way to {@code target}; a target different from the last one restarts.
	 *
	 * @param target the reverb to reach
	 * @param nowMs the current time, in milliseconds
	 * @return the reverb now
	 */
	public ReverbParams at(ReverbParams target, long nowMs) {
		if (!target.equals(this.target)) {
			start = current;
			this.target = target;
			startMs = nowMs;
		}
		if (!settled()) {
			double fraction = Math.clamp((nowMs - startMs) / GLIDE_MS, 0, 1);
			current = fraction >= 1 ? target : start.toward(target, fraction);
		}
		return current;
	}

	/**
	 * Whether the reverb has reached its target.
	 *
	 * @return true once the target is reached
	 */
	public boolean settled() {
		return current.equals(target);
	}

	/**
	 * Delays the glide by {@code ms}, e.g. the time the game was paused.
	 *
	 * @param ms the delay, in milliseconds
	 */
	public void shift(long ms) {
		startMs += ms;
	}
}
