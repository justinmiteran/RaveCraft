package com.ravecraft.mod.core.audio.opus;

import com.ravecraft.mod.core.audio.stream.StreamFormat;
import io.github.jaredmdobson.concentus.OpusDecoder;
import io.github.jaredmdobson.concentus.OpusException;

/** Decodes Opus frames back to 20 ms of mono 48 kHz audio, concealing lost frames. Not thread-safe. */
public final class OpusMonoDecoder {
	private final OpusDecoder decoder;

	/** Creates a mono 48 kHz decoder. */
	public OpusMonoDecoder() {
		try {
			decoder = new OpusDecoder(StreamFormat.SAMPLE_RATE, 1);
		} catch (OpusException e) {
			throw new IllegalStateException("Cannot create Opus decoder", e);
		}
	}

	/**
	 * Decodes {@code frame}, or conceals a lost frame when it is null. Corrupt data gives silence.
	 *
	 * @param frame the Opus frame, or null when lost
	 * @return the decoded samples
	 */
	public short[] decode(byte[] frame) {
		short[] pcm = new short[StreamFormat.FRAME_SAMPLES];
		try {
			if (frame == null) {
				decoder.decode(null, 0, 0, pcm, 0, StreamFormat.FRAME_SAMPLES, false);
			} else {
				decoder.decode(frame, 0, frame.length, pcm, 0, StreamFormat.FRAME_SAMPLES, false);
			}
		} catch (OpusException | RuntimeException e) {
			return new short[StreamFormat.FRAME_SAMPLES];
		}
		return pcm;
	}
}
