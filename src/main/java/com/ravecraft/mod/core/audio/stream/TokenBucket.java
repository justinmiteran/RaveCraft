package com.ravecraft.mod.core.audio.stream;

/** Rate limiter: refills continuously at {@code ratePerSecond}, holds at most {@code capacity}, starts full. */
public final class TokenBucket {
	private final double ratePerNano;
	private final long capacity;
	private double tokens;
	private long lastNanos;

	public TokenBucket(long ratePerSecond, long capacity, long nowNanos) {
		this.ratePerNano = ratePerSecond / 1e9;
		this.capacity = capacity;
		this.tokens = capacity;
		this.lastNanos = nowNanos;
	}

	/** Takes {@code amount} tokens if available; otherwise takes nothing and returns false. */
	public boolean tryConsume(long amount, long nowNanos) {
		if (nowNanos > lastNanos) {
			tokens = Math.min(capacity, tokens + (nowNanos - lastNanos) * ratePerNano);
			lastNanos = nowNanos;
		}
		if (tokens < amount) {
			return false;
		}
		tokens -= amount;
		return true;
	}
}
