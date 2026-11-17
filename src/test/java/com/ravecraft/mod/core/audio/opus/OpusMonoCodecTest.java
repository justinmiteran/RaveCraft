package com.ravecraft.mod.core.audio.opus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ravecraft.mod.core.audio.stream.StreamFormat;
import java.util.Random;
import org.junit.jupiter.api.Test;

class OpusMonoCodecTest {
	static short[] sineFrame(int index, double frequency, int amplitude) {
		short[] frame = new short[StreamFormat.FRAME_SAMPLES];
		for (int i = 0; i < frame.length; i++) {
			long n = (long) index * StreamFormat.FRAME_SAMPLES + i;
			frame[i] = (short) Math.round(amplitude * Math.sin(2 * Math.PI * frequency * n / StreamFormat.SAMPLE_RATE));
		}
		return frame;
	}

	static double rms(short[] samples) {
		double sum = 0;
		for (short s : samples) {
			sum += (double) s * s;
		}
		return Math.sqrt(sum / samples.length);
	}

	static int zeroCrossings(short[] samples, short previous) {
		int count = 0;
		short last = previous;
		for (short s : samples) {
			if ((last < 0 && s >= 0) || (last >= 0 && s < 0)) {
				count++;
			}
			last = s;
		}
		return count;
	}

	@Test
	void encodedFrameFitsCap() {
		OpusMonoEncoder encoder = new OpusMonoEncoder();
		Random random = new Random(1);
		for (int f = 0; f < 50; f++) {
			short[] frame = new short[StreamFormat.FRAME_SAMPLES];
			for (int i = 0; i < frame.length; i++) {
				frame[i] = (short) random.nextInt(Short.MIN_VALUE, Short.MAX_VALUE + 1);
			}
			int length = encoder.encode(frame).length;
			assertTrue(length >= 1 && length <= StreamFormat.MAX_FRAME_BYTES, "frame " + f + " is " + length + " bytes");
		}
	}

	@Test
	void roundTripKeepsTone() {
		OpusMonoEncoder encoder = new OpusMonoEncoder();
		OpusMonoDecoder decoder = new OpusMonoDecoder();
		double inputRms = 10_000 / Math.sqrt(2);
		int crossings = 0;
		double rmsSum = 0;
		short previous = 0;
		for (int f = 0; f < 50; f++) {
			short[] decoded = decoder.decode(encoder.encode(sineFrame(f, 1000, 10_000)));
			assertEquals(StreamFormat.FRAME_SAMPLES, decoded.length);
			if (f >= 5) {
				crossings += zeroCrossings(decoded, previous);
				rmsSum += rms(decoded);
			}
			previous = decoded[decoded.length - 1];
		}
		double outputRms = rmsSum / 45;
		assertEquals(inputRms, outputRms, inputRms * 0.3);
		assertEquals(1800, crossings, 1800 * 0.05);
	}

	@Test
	void lostFrameConcealsWithoutThrowing() {
		OpusMonoEncoder encoder = new OpusMonoEncoder();
		OpusMonoDecoder decoder = new OpusMonoDecoder();
		for (int f = 0; f < 10; f++) {
			decoder.decode(encoder.encode(sineFrame(f, 1000, 10_000)));
		}
		assertEquals(StreamFormat.FRAME_SAMPLES, decoder.decode(null).length);
	}

	@Test
	void corruptFrameGivesSilence() {
		short[] decoded = new OpusMonoDecoder().decode(new byte[]{(byte) 0xFF, 1, 2});
		assertEquals(StreamFormat.FRAME_SAMPLES, decoded.length);
	}

	@Test
	void wrongFrameSizeRejected() {
		assertThrows(IllegalArgumentException.class, () -> new OpusMonoEncoder().encode(new short[100]));
	}
}
