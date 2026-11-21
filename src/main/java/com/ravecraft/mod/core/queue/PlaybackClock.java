package com.ravecraft.mod.core.queue;

/** Estimates where a track is: the position it started from plus the time elapsed since its session opened. */
public record PlaybackClock(long startPositionMs, long startedAtMs) {
	/** The estimated position at {@code nowMs}; never before the start position. */
	public long positionAt(long nowMs) {
		return startPositionMs + Math.max(0, nowMs - startedAtMs);
	}
}
