package com.ravecraft.mod.core;

/**
 * Example of pure logic: a tick cooldown. Replace with your own; keep this package free of Minecraft imports.
 */
public final class Cooldown {
	private final int ticks;
	private long readyAt;

	public Cooldown(int ticks) {
		if (ticks < 0) {
			throw new IllegalArgumentException("ticks must be >= 0: " + ticks);
		}
		this.ticks = ticks;
	}

	/** Returns true and restarts the cooldown if it has elapsed at {@code gameTime}. */
	public boolean tryUse(long gameTime) {
		if (gameTime < readyAt) {
			return false;
		}
		readyAt = gameTime + ticks;
		return true;
	}
}
