package com.ravecraft.mod.core.acoustics.reverb;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChorusTest {
	private static final double RATE = 48_000;
	private static final FluidReverb ON = new FluidReverb("water", 1.2, 1, 1, 0.4, 0.3, true, 0.4, 1.5);

	/** Runs {@code seconds} of silence through the chorus so its mix has fully glided on. */
	private static void settle(Chorus chorus, double seconds) {
		float[] block = new float[4_800];
		for (int i = 0; i < seconds * 10; i++) {
			chorus.process(block, block.length, ON);
		}
	}

	@Test
	void offLeavesTheSignalUntouched() {
		float[] input = new float[4_800];
		for (int i = 0; i < input.length; i++) {
			input[i] = (float) Math.sin(i * 0.05);
		}
		float[] a = input.clone();
		new Chorus(RATE).process(a, a.length, null);
		assertArrayEquals(input, a);
		float[] b = input.clone();
		new Chorus(RATE).process(b, b.length, new FluidReverb("water", 1.2, 1, 1, 0.4, 0.3, false, 0.4, 1.5));
		assertArrayEquals(input, b);
	}

	@Test
	void onDelaysByAtMostBasePlusDepth() {
		Chorus chorus = new Chorus(RATE);
		settle(chorus, 1);
		float[] block = new float[4_800];
		block[0] = 1;
		chorus.process(block, block.length, ON);
		double lo = (8 - 1.5) * RATE / 1000 - 2;
		double hi = (8 + 1.5) * RATE / 1000 + 2;
		double energy = 0;
		for (int i = 0; i < block.length; i++) {
			if (block[i] != 0) {
				assertTrue(i >= lo && i <= hi, "energy at sample " + i);
				energy += block[i] * block[i];
			}
		}
		assertTrue(energy > 0.1, "impulse should be heard");
	}

	@Test
	void pitchWobbles() {
		Chorus chorus = new Chorus(RATE);
		settle(chorus, 1);
		int total = (int) (3 * RATE);
		float[] sig = new float[total];
		for (int i = 0; i < total; i++) {
			sig[i] = (float) Math.sin(2 * Math.PI * 1000 * i / RATE);
		}
		for (int off = 0; off < total; off += 4_800) {
			float[] part = new float[4_800];
			System.arraycopy(sig, off, part, 0, 4_800);
			chorus.process(part, part.length, ON);
			System.arraycopy(part, 0, sig, off, 4_800);
		}
		double min = Double.MAX_VALUE;
		double max = 0;
		double last = -1;
		for (int i = 1; i < total; i++) {
			if (sig[i - 1] < 0 && sig[i] >= 0) {
				double t = i - 1 + -sig[i - 1] / (double) (sig[i] - sig[i - 1]);
				if (last >= 0) {
					double d = t - last;
					min = Math.min(min, d);
					max = Math.max(max, d);
				}
				last = t;
			}
		}
		assertTrue((max - min) / min > 0.001, "spacing varies by " + (max - min) / min);
	}

	@Test
	void switchingOnAndOffDoesNotClick() {
		Chorus chorus = new Chorus(RATE);
		double w = 2 * Math.PI * 200 / RATE;
		double inputStep = Math.sin(w) ;
		long n = 0;
		float prev = 0;
		boolean first = true;
		double maxStep = 0;
		FluidReverb[] states = {null, ON, null, ON};
		for (FluidReverb state : states) {
			for (int b = 0; b < 10; b++) {
				float[] block = new float[4_800];
				for (int i = 0; i < block.length; i++) {
					block[i] = (float) Math.sin(w * n++);
				}
				chorus.process(block, block.length, state);
				for (float s : block) {
					if (!first) {
						maxStep = Math.max(maxStep, Math.abs(s - prev));
					}
					first = false;
					prev = s;
				}
			}
		}
		assertTrue(maxStep <= 1.5 * inputStep, "max step " + maxStep + " vs input " + inputStep);
	}
}
