package com.ravecraft.mod.core.acoustics.radiation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ravecraft.mod.core.acoustics.Decibels;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RadiationFilterTest {
	private static final int RATE = 48_000;
	private static final int BLOCK = 4800;
	/** The party speaker straight behind, relative to its bass. */
	private static final BandGains BEHIND = new BandGains(1, Decibels.toAmplitude(-9.6), Decibels.toAmplitude(-25.3));

	@Test
	void eachBandGetsItsGain() {
		assertEquals(0, level(60, BEHIND), 1);
		assertEquals(-9.6, level(1000, BEHIND), 1);
		assertEquals(-25.3, level(8000, BEHIND), 1);
	}

	@Test
	void equalGainsPassTheSignalUntouched() {
		float[] samples = new float[BLOCK];
		Random random = new Random(1);
		for (int i = 0; i < samples.length; i++) {
			samples[i] = random.nextFloat() * 2 - 1;
		}
		float[] expected = samples.clone();
		new RadiationFilter(RATE).process(samples, samples.length, BandGains.FLAT);
		assertArrayEquals(expected, samples);
	}

	@Test
	void firstBlockTakesTheGainsAtOnce() {
		float[] input = sine(8000, 0.5, BLOCK);
		float[] samples = input.clone();
		new RadiationFilter(RATE).process(samples, samples.length, BEHIND);
		assertEquals(-25.3, rmsDb(samples, 480, 960) - rmsDb(input, 480, 960), 1.5);
	}

	@Test
	void blocksJoinWithoutJumps() {
		float[] whole = sine(700, 0.5, 2 * BLOCK);
		float[] first = Arrays.copyOfRange(whole, 0, BLOCK);
		float[] second = Arrays.copyOfRange(whole, BLOCK, 2 * BLOCK);
		new RadiationFilter(RATE).process(whole, whole.length, BEHIND);
		RadiationFilter filter = new RadiationFilter(RATE);
		filter.process(first, BLOCK, BEHIND);
		filter.process(second, BLOCK, BEHIND);
		assertArrayEquals(Arrays.copyOfRange(whole, 0, BLOCK), first, 1e-6f);
		assertArrayEquals(Arrays.copyOfRange(whole, BLOCK, 2 * BLOCK), second, 1e-6f);
	}

	@Test
	void fastChangesStayBoundedWithoutJumps() {
		RadiationFilter filter = new RadiationFilter(RATE);
		float[] input = sine(200, 1, 20 * BLOCK);
		float last = 0;
		for (int block = 0; block < 20; block++) {
			float[] samples = Arrays.copyOfRange(input, block * BLOCK, (block + 1) * BLOCK);
			filter.process(samples, BLOCK, block % 2 == 0 ? BandGains.FLAT : BEHIND);
			for (float sample : samples) {
				assertTrue(Math.abs(sample) <= 1, "sample " + sample);
				assertTrue(Math.abs(sample - last) < 0.05, "step from " + last + " to " + sample);
				last = sample;
			}
		}
	}

	/** The level of a sine through the filter once settled, in dB from the input. */
	private static double level(double hz, BandGains gains) {
		RadiationFilter filter = new RadiationFilter(RATE);
		float[] input = sine(hz, 0.5, 10 * BLOCK);
		float[] output = input.clone();
		filter.process(output, output.length, gains);
		return rmsDb(output, 9 * BLOCK, 10 * BLOCK) - rmsDb(input, 9 * BLOCK, 10 * BLOCK);
	}

	private static float[] sine(double hz, double amplitude, int count) {
		float[] samples = new float[count];
		for (int i = 0; i < count; i++) {
			samples[i] = (float) (amplitude * Math.sin(2 * Math.PI * hz * i / RATE));
		}
		return samples;
	}

	private static double rmsDb(float[] samples, int from, int to) {
		double sum = 0;
		for (int i = from; i < to; i++) {
			sum += samples[i] * samples[i];
		}
		return Decibels.fromPower(sum / (to - from));
	}
}
