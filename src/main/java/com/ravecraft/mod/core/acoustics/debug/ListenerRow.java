package com.ravecraft.mod.core.acoustics.debug;

/**
 * What the listener hears through: the medium around the head and its losses, the chorus, the player's acoustic
 * effects option, EFX and its sends per source, and whether the server turned occlusion and reverb on.
 */
public record ListenerRow(String medium, double lossDb, double highLossDb, double blur, boolean chorus,
		boolean effectsOption, boolean efx, int sends, boolean occlusion, boolean reverb) {
}
