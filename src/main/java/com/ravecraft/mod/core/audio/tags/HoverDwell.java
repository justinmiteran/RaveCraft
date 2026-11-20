package com.ravecraft.mod.core.audio.tags;

/**
 * Decides when a hovered row has been under the mouse long enough to be worth a disk read. Asked once per frame
 * while a tooltip is wanted; a different key, or a pause in the requests, restarts the wait. Single thread.
 */
public final class HoverDwell {
	public static final long DELAY_MS = 300;
	/** Longer than this between two requests means the mouse left the row meanwhile. */
	public static final long MAX_GAP_MS = 500;

	private String key;
	private long since;
	private long lastSeen;

	/** True once {@code key} has been requested continuously for {@link #DELAY_MS}. */
	public boolean ready(String key, long nowMs) {
		if (!key.equals(this.key) || nowMs - lastSeen > MAX_GAP_MS) {
			this.key = key;
			since = nowMs;
		}
		lastSeen = nowMs;
		return nowMs - since >= DELAY_MS;
	}
}
