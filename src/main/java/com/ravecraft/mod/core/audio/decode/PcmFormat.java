package com.ravecraft.mod.core.audio.decode;

/** Shape of the 16-bit signed little-endian PCM a decoder produces. */
public record PcmFormat(int sampleRate, int channels) {
	/** Bytes in one frame: one 16-bit sample per channel. */
	public int frameBytes() {
		return channels * 2;
	}
}
