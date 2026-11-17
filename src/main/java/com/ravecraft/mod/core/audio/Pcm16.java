package com.ravecraft.mod.core.audio;

import java.util.Arrays;

/** Helpers for 16-bit signed little-endian interleaved PCM. */
public final class Pcm16 {
	private Pcm16() {
	}

	/**
	 * Averages the channels of each frame into one mono sample. OpenAL only spatialises mono sources, so every
	 * stream goes through this before reaching the sound engine.
	 */
	public static byte[] downmixToMono(byte[] src, int length, int channels) {
		if (channels <= 1) {
			return Arrays.copyOf(src, length);
		}
		int frameBytes = channels * 2;
		int frames = length / frameBytes;
		byte[] mono = new byte[frames * 2];
		for (int frame = 0; frame < frames; frame++) {
			int base = frame * frameBytes;
			int sum = 0;
			for (int channel = 0; channel < channels; channel++) {
				int at = base + channel * 2;
				sum += (short) ((src[at] & 0xFF) | (src[at + 1] << 8));
			}
			int sample = Math.clamp(sum / channels, Short.MIN_VALUE, Short.MAX_VALUE);
			mono[frame * 2] = (byte) sample;
			mono[frame * 2 + 1] = (byte) (sample >> 8);
		}
		return mono;
	}
}
