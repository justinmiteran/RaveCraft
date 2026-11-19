package com.ravecraft.mod.core.acoustics.debug;

/**
 * The acoustic engine's cost: voxels used last tick against their budget for occlusion and reverb, the room probes
 * still running, and the audio thread's step time.
 */
public record PerfRow(int occlusionVoxels, int occlusionBudget, int reverbVoxels, int reverbBudget, int probesRunning,
		double stepAverageMs, double stepMaxMs, boolean audioRunning) {
}
