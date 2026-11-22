package com.ravecraft.mod.core.acoustics.debug;

/**
 * Times a repeating step over one-second windows: the average of the last finished window (of the current one before
 * any finished), and the largest step of the last finished window and the current one. Not thread-safe.
 */
public final class StepTimer {
	private static final long WINDOW_MS = 1_000;
	private static final double NANOS_PER_MS = 1e6;

	private long windowStart = Long.MIN_VALUE;
	private long count;
	private long totalNanos;
	private long maxNanos;
	private double previousAverageMs = -1;
	private long previousMaxNanos;

	/**
	 * Records one step of {@code nanos}, closing the window once a second has passed.
	 *
	 * @param nanos the step's duration, in nanoseconds
	 * @param nowMs the current time, in milliseconds
	 */
	public void record(long nanos, long nowMs) {
		if (windowStart == Long.MIN_VALUE) {
			windowStart = nowMs;
		} else if (nowMs - windowStart >= WINDOW_MS) {
			previousAverageMs = count == 0 ? 0 : totalNanos / NANOS_PER_MS / count;
			previousMaxNanos = maxNanos;
			windowStart = nowMs;
			count = 0;
			totalNanos = 0;
			maxNanos = 0;
		}
		count++;
		totalNanos += nanos;
		maxNanos = Math.max(maxNanos, nanos);
	}

	/**
	 * The average step of the last finished window, or of the current one before any finished.
	 *
	 * @return the average step, in milliseconds
	 */
	public double averageMs() {
		if (previousAverageMs >= 0) {
			return previousAverageMs;
		}
		return count == 0 ? 0 : totalNanos / NANOS_PER_MS / count;
	}

	/**
	 * The largest step of the last finished window and the current one.
	 *
	 * @return the largest step, in milliseconds
	 */
	public double maxMs() {
		return Math.max(previousMaxNanos, maxNanos) / NANOS_PER_MS;
	}
}
