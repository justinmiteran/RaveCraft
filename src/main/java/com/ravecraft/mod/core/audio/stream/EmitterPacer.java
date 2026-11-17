package com.ravecraft.mod.core.audio.stream;

/** Keeps an emitter at most {@code leadMillis} ahead of real time. Time is passed in, so this is pure. */
public final class EmitterPacer {
	private final long leadMillis;

	public EmitterPacer(long leadMillis) {
		this.leadMillis = leadMillis;
	}

	/**
	 * How long to wait before sending audio that would bring the total sent to {@code sentAudioMillis}, when
	 * {@code elapsedMillis} of real time have passed since the stream started.
	 */
	public long delayBeforeNextMillis(long elapsedMillis, long sentAudioMillis) {
		return Math.max(0, sentAudioMillis - elapsedMillis - leadMillis);
	}
}
