package com.ravecraft.mod.core.acoustics.occlusion;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OcclusionFilterTest {
	private static final int RATE = 48_000;

	private static float[] sine(double hz, int samples) {
		float[] out = new float[samples];
		for (int i = 0; i < samples; i++) {
			out[i] = (float) (0.5 * Math.sin(2 * Math.PI * hz * i / RATE));
		}
		return out;
	}

	/** RMS level of the second half, in dB. */
	private static double levelDb(float[] samples) {
		double sum = 0;
		int from = samples.length / 2;
		for (int i = from; i < samples.length; i++) {
			sum += samples[i] * samples[i];
		}
		return 10 * Math.log10(sum / (samples.length - from));
	}

	/** Level change of a steady sine through a filter held at {@code highLossDb}. */
	private static double changeDb(double hz, double highLossDb) {
		OcclusionFilter filter = new OcclusionFilter(RATE);
		float[] in = sine(hz, RATE);
		float[] out = in.clone();
		filter.process(out, out.length, highLossDb); // glides from 0 over this first block
		float[] steady = in.clone();
		filter.process(steady, steady.length, highLossDb);
		return levelDb(steady) - levelDb(in);
	}

	@Test
	void losesExactlyTheHighLossAtTheReferenceFrequency() {
		assertEquals(-30, changeDb(OcclusionFilter.REFERENCE_HZ, 30), 0.5);
		assertEquals(-12, changeDb(OcclusionFilter.REFERENCE_HZ, 12), 0.5);
	}

	@Test
	void fallsSixDbPerOctaveAboveTheCutoff() {
		double at4k = changeDb(4000, 30);
		double at8k = changeDb(8000, 30);
		assertEquals(-6, at8k - at4k, 0.7);
	}

	@Test
	void bassGoesThroughOneStoneBlock() {
		assertTrue(changeDb(100, 30) > -3, "100 Hz behind stone: " + changeDb(100, 30));
	}

	@Test
	void noLossLeavesTheSignalUntouched() {
		OcclusionFilter filter = new OcclusionFilter(RATE);
		float[] in = sine(1000, 4800);
		float[] out = in.clone();
		filter.process(out, out.length, 0);
		assertArrayEquals(in, out);
	}

	@Test
	void lossIsCappedAtTheMinimumCutoff() {
		assertEquals(changeDb(OcclusionFilter.REFERENCE_HZ, 200), changeDb(OcclusionFilter.REFERENCE_HZ, 1000), 0.01);
		assertTrue(changeDb(OcclusionFilter.MIN_CUTOFF_HZ, 200) > -4, "the floor cutoff still passes");
	}

	@Test
	void changingTheLossDoesNotClick() {
		OcclusionFilter filter = new OcclusionFilter(RATE);
		float[] in = sine(200, 3 * 4800);
		float[] out = in.clone();
		// 100 ms blocks: unmuffled, then a door closes (stone), then it opens again.
		double[] losses = {0, 30, 0};
		for (int b = 0; b < 3; b++) {
			float[] block = new float[4800];
			System.arraycopy(out, b * 4800, block, 0, 4800);
			filter.process(block, 4800, losses[b]);
			System.arraycopy(block, 0, out, b * 4800, 4800);
		}
		double maxInputStep = 2 * Math.PI * 200 / RATE * 0.5;
		for (int i = 1; i < out.length; i++) {
			assertTrue(Math.abs(out[i] - out[i - 1]) <= 1.5 * maxInputStep, "jump at sample " + i);
		}
	}
}
