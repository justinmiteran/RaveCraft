package com.ravecraft.mod.core.acoustics;

/** After a failure, waits a fixed time before allowing another try. Not thread-safe. */
public final class RetryCooldown {
	private final long cooldownMs;
	private long retryAtMs = Long.MIN_VALUE;

	public RetryCooldown(long cooldownMs) {
		this.cooldownMs = cooldownMs;
	}

	public boolean canTry(long nowMs) {
		return nowMs >= retryAtMs;
	}

	public void failed(long nowMs) {
		retryAtMs = nowMs + cooldownMs;
	}
}
