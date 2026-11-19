package com.ravecraft.mod.core.acoustics.reverb;

/** A distinct echo: its round-trip {@code delaySeconds} and its {@code gain} in [0, 1]. */
public record Echo(double delaySeconds, double gain) {
	/** No echo. */
	public static final Echo NONE = new Echo(0, 0);
}
