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
	private static final double MS_PER_SECOND = 1000;
	/** Spare samples at the end of the delay line, beyond the deepest tap. */
	private static final int LINE_SLACK = 64;
	private static final double MIN_RATE_HZ = 0.05;
	private static final double MAX_RATE_HZ = 5;
	private static final double TWO_PI = 2 * Math.PI;

	private final double sampleRate;
	private final float[] line;
	private final double mixStep;
	private int write;
	private double phase;
	private double mix;
	private double rateHz = 0.4;
	private double depthMs = 1.5;

	/**
	 * Creates a silent chorus.
	 *
	 * @param sampleRate the stream's sample rate in hertz
	 */
	public Chorus(double sampleRate) {
		this.sampleRate = sampleRate;
		this.line = new float[(int) Math.ceil((BASE_DELAY_MS + MAX_DEPTH_MS) * sampleRate / MS_PER_SECOND)
				+ LINE_SLACK];
		this.mixStep = MS_PER_SECOND / (GLIDE_MS * sampleRate);
	}

	/**
	 * Processes {@code count} samples in place; active when {@code fluid} is non-null and has its chorus on.
	 *
	 * @param samples the samples to process
	 * @param count how many samples to process
	 * @param fluid the fluid's reverb, or null
	 */
	public void process(float[] samples, int count, @Nullable FluidReverb fluid) {
		boolean active = fluid != null && fluid.chorus();
		if (active) {
			configure(fluid);
		}
		if (!active && mix <= 0) {
			idle(samples, count);
			return;
		}
		double target = active ? 1 : 0;
		double phaseStep = TWO_PI * rateHz / sampleRate;
		double base = BASE_DELAY_MS * sampleRate / MS_PER_SECOND;
		double depth = depthMs * sampleRate / MS_PER_SECOND;
		for (int i = 0; i < count; i++) {
			float dry = samples[i];
			line[write] = dry;
			float wet = tap(Math.clamp(base + depth * Math.sin(phase), 1, line.length - 2));
			samples[i] = (float) (dry * (1 - mix) + wet * mix);
			write = next(write);
			phase += phaseStep;
			if (phase > TWO_PI) {
				phase -= TWO_PI;
			}
			glide(target);
		}
	}

	/** Takes the rate and depth of {@code fluid}, bounded. */
	private void configure(FluidReverb fluid) {
		double rate = fluid.chorusRateHz();
		double depth = fluid.chorusDepthMs();
		rateHz = Double.isFinite(rate) ? Math.clamp(rate, MIN_RATE_HZ, MAX_RATE_HZ) : MIN_RATE_HZ;
		// The tap may never reach the write head, so the depth stops one sample short of the base delay.
		double maxDepth = Math.min(MAX_DEPTH_MS, BASE_DELAY_MS - MS_PER_SECOND / sampleRate);
		depthMs = Double.isFinite(depth) ? Math.clamp(depth, 0, maxDepth) : 0;
	}

	/** Only keeps the line current. */
	private void idle(float[] samples, int count) {
		for (int i = 0; i < count; i++) {
			line[write] = samples[i];
			write = next(write);
		}
	}

	/** The line read {@code delay} samples behind the write head, interpolated. */
	private float tap(double delay) {
		int n = line.length;
		double pos = write - delay;
		if (pos < 0) {
			pos += n;
		}
		int i0 = (int) pos;
		double frac = pos - i0;
		int i1 = next(i0);
		return (float) (line[i0] * (1 - frac) + line[i1] * frac);
	}

	/** Moves the mix one step toward {@code target}. */
	private void glide(double target) {
		if (mix < target) {
			mix = Math.min(target, mix + mixStep);
		} else if (mix > target) {
			mix = Math.max(target, mix - mixStep);
		}
	}

	private int next(int index) {
		return index + 1 == line.length ? 0 : index + 1;
	}
}
