package com.ravecraft.mod.core.audio.stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class LinearResamplerTest {
	private static short[] sine(int rate, double frequency, int samples) {
		short[] out = new short[samples];
		for (int i = 0; i < samples; i++) {
			out[i] = (short) Math.round(10_000 * Math.sin(2 * Math.PI * frequency * i / rate));
		}
		return out;
	}

	private static int zeroCrossings(short[] samples) {
		int count = 0;
		for (int i = 1; i < samples.length; i++) {
			if ((samples[i - 1] < 0) != (samples[i] < 0)) {
				count++;
			}
		}
		return count;
	}

	private static short[] concat(short[] a, short[] b) {
		short[] out = Arrays.copyOf(a, a.length + b.length);
		System.arraycopy(b, 0, out, a.length, b.length);
		return out;
	}

	@Test
	void resamplingKeepsFrequency() {
		short[] input = sine(44_100, 1000, 44_100);
		LinearResampler resampler = new LinearResampler(44_100);
		int[] blocks = {1000, 4410, 3, 9000, 12_345, 7, 17_335};
		short[] output = new short[0];
		int at = 0;
		for (int block : blocks) {
			short[] part = Arrays.copyOfRange(input, at, at + block);
			output = concat(output, resampler.process(part, part.length));
			at += block;
		}
		assertEquals(44_100, at);
		assertEquals(48_000, output.length, 2);
		assertEquals(2_000, zeroCrossings(output), 4);
	}

	@Test
	void sameRateIsIdentity() {
		short[] input = sine(48_000, 440, 1234);
		assertArrayEquals(input, new LinearResampler(48_000).process(input, input.length));
	}

	@Test
	void upsampleFrom8k() {
		LinearResampler resampler = new LinearResampler(8_000);
		short[] input = sine(8_000, 200, 8_000);
		// The last input sample waits for the next block (streaming), which holds back up to 6 outputs at 8 kHz.
		assertEquals(48_000, resampler.process(input, input.length).length, 6);
	}
}
