package com.ravecraft.mod.core.acoustics.reverb;

/**
 * The long-distance echo: a delayed repeat heard when a far, hard surface throws the sound back. It is considered
 * between {@code minDistance} and {@code maxDistance} blocks, needs at least {@code minRays} reflecting rays, and
 * surfaces absorbing more than {@code maxAbsorption} give none. {@code gain}, {@code feedback} and {@code damping}
 * shape the repeat.
 */
public record EchoSettings(double minDistance, double maxDistance, int minRays, double maxAbsorption, double gain,
		double feedback, double damping) {
	public static final EchoSettings DEFAULT = new EchoSettings(17, 35, 4, 0.2, 0.6, 0.15, 0.5);
}
