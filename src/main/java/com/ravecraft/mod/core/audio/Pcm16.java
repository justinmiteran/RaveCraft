package com.ravecraft.mod.core.audio;

import java.util.Arrays;

/** Helpers for 16-bit signed little-endian interleaved PCM. */
public final class Pcm16 {
	/** Bytes in one 16-bit sample. */
	public static final int SAMPLE_BYTES = 2;
	private static final int BYTE_BITS = 8;
	private static final int BYTE_MASK = 0xFF;

	private Pcm16() {
	}

	/**
	 * Reads the 16-bit little-endian sample at {@code at}.
	 *
	 * @param le the PCM bytes
	 * @param at the index of the sample's low byte
	 * @return the signed sample
	 */
	public static short sampleAt(byte[] le, int at) {
		return (short) (le[at] & BYTE_MASK | le[at + 1] << BYTE_BITS);
	}

	/**
	 * Writes {@code sample} as 16-bit little-endian at {@code at}; bits above the low 16 are dropped.
	 *
	 * @param le the PCM bytes
	 * @param at the index of the sample's low byte
	 * @param sample the sample
	 */
	public static void put(byte[] le, int at, int sample) {
		le[at] = (byte) sample;
		le[at + 1] = (byte) (sample >> BYTE_BITS);
	}

	/**
	 * Averages the channels of each frame into one mono sample. OpenAL only spatialises mono sources, so every
	 * stream goes through this before reaching the sound engine.
	 *
	 * @param src interleaved PCM
	 * @param length the bytes of {@code src} to read
	 * @param channels the channels per frame
	 * @return mono PCM, one sample per frame
	 */
	public static byte[] downmixToMono(byte[] src, int length, int channels) {
		if (channels <= 1) {
			return Arrays.copyOf(src, length);
		}
		int frameBytes = channels * SAMPLE_BYTES;
		int frames = length / frameBytes;
		byte[] mono = new byte[frames * SAMPLE_BYTES];
		for (int frame = 0; frame < frames; frame++) {
			int base = frame * frameBytes;
			int sum = 0;
			for (int channel = 0; channel < channels; channel++) {
				sum += sampleAt(src, base + channel * SAMPLE_BYTES);
			}
			put(mono, frame * SAMPLE_BYTES, Math.clamp(sum / channels, Short.MIN_VALUE, Short.MAX_VALUE));
		}
		return mono;
	}

	/**
	 * Reads {@code length} bytes of 16-bit little-endian PCM as samples; a trailing odd byte is ignored.
	 *
	 * @param le the PCM bytes
	 * @param length the bytes to read
	 * @return the samples
	 */
	public static short[] toShorts(byte[] le, int length) {
		short[] out = new short[length / SAMPLE_BYTES];
		for (int i = 0; i < out.length; i++) {
			out[i] = sampleAt(le, i * SAMPLE_BYTES);
		}
		return out;
	}
}
