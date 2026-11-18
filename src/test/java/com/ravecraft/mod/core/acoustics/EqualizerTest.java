package com.ravecraft.mod.core.acoustics;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EqualizerTest {
	private static final int RATE = 48_000;

	private static float[] sine(double hz, double amplitude, int samples) {
		float[] out = new float[samples];
		for (int i = 0; i < samples; i++) {
			out[i] = (float) (amplitude * Math.sin(2 * Math.PI * hz * i / RATE));
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

	private static double changeDb(Equalizer eq, double hz) {
		float[] in = sine(hz, 0.5, RATE);
		float[] out = in.clone();
		eq.process(out, out.length);
		return levelDb(out) - levelDb(in);
	}

	@Test
	void bassCutAt60Hz() {
		double change = changeDb(new Equalizer(RATE, 150, -6), 60);
		assertTrue(change >= -6.5 && change <= -4.5, "change " + change);
	}

	@Test
	void midUntouchedAt1kHz() {
		double change = changeDb(new Equalizer(RATE, 150, -6), 1000);
		assertTrue(Math.abs(change) <= 0.5, "change " + change);
	}

	@Test
	void zeroGainIsIdentity() {
		float[] in = sine(60, 0.5, 4800);
		float[] out = in.clone();
		new Equalizer(RATE, 150, 0).process(out, out.length);
		assertArrayEquals(in, out, 1e-6f);
	}

	@Test
	void stateCarriesAcrossBlocks() {
		float[] whole = sine(80, 0.5, 4800);
		float[] blocks = whole.clone();
		new Equalizer(RATE, 150, -6).process(whole, whole.length);
		Equalizer eq = new Equalizer(RATE, 150, -6);
		float[] block = new float[100];
		for (int b = 0; b < 48; b++) {
			System.arraycopy(blocks, b * 100, block, 0, 100);
			eq.process(block, 100);
			System.arraycopy(block, 0, blocks, b * 100, 100);
		}
		assertArrayEquals(whole, blocks, 1e-6f);
	}

	@Test
	void outputClamped() {
		float[] square = new float[4800];
		for (int i = 0; i < square.length; i++) {
			square[i] = (i / 160) % 2 == 0 ? 1f : -1f;
		}
		new Equalizer(RATE, 150, 6).process(square, square.length);
		for (float s : square) {
			assertTrue(s >= -1f && s <= 1f, "sample " + s);
		}
	}
}
