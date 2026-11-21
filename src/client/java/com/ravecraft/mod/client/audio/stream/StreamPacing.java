package com.ravecraft.mod.client.audio.stream;

/**
 * The one place allowed to call {@link Thread#sleep}: it paces the emitter's own worker thread,
 * which sends audio no faster than real time. Never call it from the client or server thread.
 */
final class StreamPacing {
	private StreamPacing() {
	}

	/** Blocks the calling emitter thread for {@code millis} milliseconds, if positive. */
	static void sleep(long millis) throws InterruptedException {
		if (millis > 0) {
			Thread.sleep(millis);
		}
	}
}
