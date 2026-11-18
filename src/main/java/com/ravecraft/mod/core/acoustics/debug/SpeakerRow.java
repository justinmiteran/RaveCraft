package com.ravecraft.mod.core.acoustics.debug;

/**
 * One audible speaker as the debug screen shows it: its session, distance (blocks), heard gain (linear, after
 * propagation and occlusion), whether it holds a sound source, its occlusion when traced, and its reverb slot
 * (0 = the listener's space, 1 = S1, 2 = S2).
 */
public record SpeakerRow(int session, double distance, double heardGain, boolean sounding, boolean occlusionKnown,
		double lossDb, double highLossDb, int reverbSlot) {
}
