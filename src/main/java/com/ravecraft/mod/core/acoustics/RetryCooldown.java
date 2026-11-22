package com.ravecraft.mod.core.acoustics;

/** After a failure, waits a fixed time before allowing another try. Not thread-safe. */
public final class RetryCooldown {
	private final long cooldownMs;
	private long retryAtMs = Long.MIN_VALUE;

	/**
	 * Creates a cooldown that allows a first try at once.
	 *
	 * @param cooldownMs the wait after a failure
	 */
	public RetryCooldown(long cooldownMs) {
		this.cooldownMs = cooldownMs;
	}

	/**
	 * Whether the cooldown after the last failure is over.
	 *
	 * @param nowMs the current time, in milliseconds
	 * @return true when a new try may run
	 */
	public boolean canTry(long nowMs) {
		return nowMs >= retryAtMs;
	}

	/**
	 * A try failed at {@code nowMs}: the next one waits for the cooldown.
	 *
	 * @param nowMs the time of the failure, in milliseconds
	 */
	public void failed(long nowMs) {
		retryAtMs = nowMs + cooldownMs;
	}
}
