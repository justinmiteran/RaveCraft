package com.ravecraft.mod.core.acoustics.debug;

/**
 * Times a repeating step over one-second windows: the average of the last finished window (of the current one before
 * any finished), and the largest step of the last finished window and the current one. Not thread-safe.
 */
public final class StepTimer {
	private static final long WINDOW_MS = 1_000;

	private long windowStart = Long.MIN_VALUE;
	private long count;
	private long totalNanos;
	private long maxNanos;
	private double previousAverageMs = -1;
	private long previousMaxNanos;

	public void record(long nanos, long nowMs) {
		if (windowStart == Long.MIN_VALUE) {
			windowStart = nowMs;
		} else if (nowMs - windowStart >= WINDOW_MS) {
			previousAverageMs = count == 0 ? 0 : totalNanos / 1e6 / count;
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

	public double averageMs() {
		if (previousAverageMs >= 0) {
			return previousAverageMs;
		}
		return count == 0 ? 0 : totalNanos / 1e6 / count;
	}

	public double maxMs() {
		return Math.max(previousMaxNanos, maxNanos) / 1e6;
	}
}
