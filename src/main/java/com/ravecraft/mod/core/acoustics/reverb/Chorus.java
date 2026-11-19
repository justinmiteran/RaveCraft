package com.ravecraft.mod.core.acoustics.reverb;

import org.jspecify.annotations.Nullable;

/**
 * A modulated delay that gives the sound the slow pitch wobble of being underwater. The delay line is fed even when
 * the chorus is off, so switching it on has no gap; the dry/modulated mix glides over 200 ms so neither direction
 * clicks. Allocation-free per call.
 */
public final class Chorus {
	private static final double BASE_DELAY_MS = 8;
	private static final double MAX_DEPTH_MS = 10;
	private static final double GLIDE_MS = 200;

	private final double sampleRate;
	private final float[] line;
	private final double mixStep;
	private int write;
	private double phase;
	private double mix;
	private double rateHz = 0.4;
	private double depthMs = 1.5;

	public Chorus(double sampleRate) {
		this.sampleRate = sampleRate;
		this.line = new float[(int) Math.ceil((BASE_DELAY_MS + MAX_DEPTH_MS) * sampleRate / 1000) + 64];
		this.mixStep = 1000 / (GLIDE_MS * sampleRate);
	}

	/** Processes {@code count} samples in place; active when {@code fluid} is non-null and has its chorus on. */
	public void process(float[] samples, int count, @Nullable FluidReverb fluid) {
		boolean active = fluid != null && fluid.chorus();
		if (active) {
			double rate = fluid.chorusRateHz();
			double depth = fluid.chorusDepthMs();
			rateHz = Double.isFinite(rate) ? Math.clamp(rate, 0.05, 5) : 0.05;
			// The tap may never reach the write head, so the depth stops one sample short of the base delay.
			double maxDepth = Math.min(MAX_DEPTH_MS, BASE_DELAY_MS - 1000 / sampleRate);
			depthMs = Double.isFinite(depth) ? Math.clamp(depth, 0, maxDepth) : 0;
		}
		if (!active && mix <= 0) {
			// Idle: only keep the line current.
			for (int i = 0; i < count; i++) {
				line[write] = samples[i];
				write = write + 1 == line.length ? 0 : write + 1;
			}
			return;
		}
		double target = active ? 1 : 0;
		double phaseStep = 2 * Math.PI * rateHz / sampleRate;
		double base = BASE_DELAY_MS * sampleRate / 1000;
		double depth = depthMs * sampleRate / 1000;
		int n = line.length;
		for (int i = 0; i < count; i++) {
			float dry = samples[i];
			line[write] = dry;
			double delay = Math.clamp(base + depth * Math.sin(phase), 1, n - 2);
			double pos = write - delay;
			if (pos < 0) {
				pos += n;
			}
			int i0 = (int) pos;
			double frac = pos - i0;
			int i1 = i0 + 1 == n ? 0 : i0 + 1;
			float wet = (float) (line[i0] * (1 - frac) + line[i1] * frac);
			samples[i] = (float) (dry * (1 - mix) + wet * mix);
			write = write + 1 == n ? 0 : write + 1;
			phase += phaseStep;
			if (phase > 2 * Math.PI) {
				phase -= 2 * Math.PI;
			}
			if (mix < target) {
				mix = Math.min(target, mix + mixStep);
			} else if (mix > target) {
				mix = Math.max(target, mix - mixStep);
			}
		}
	}
}
