package com.ravecraft.mod.core.audio.opus;

import com.ravecraft.mod.core.audio.stream.StreamFormat;
import io.github.jaredmdobson.concentus.OpusApplication;
import io.github.jaredmdobson.concentus.OpusEncoder;
import io.github.jaredmdobson.concentus.OpusException;
import io.github.jaredmdobson.concentus.OpusSignal;
import java.util.Arrays;

/** Encodes 20 ms mono 48 kHz frames into Opus packets of at most {@link StreamFormat#MAX_FRAME_BYTES}. Not thread-safe. */
public final class OpusMonoEncoder {
	private final OpusEncoder encoder;
	private final byte[] out = new byte[StreamFormat.MAX_FRAME_BYTES];

	public OpusMonoEncoder() {
		try {
			encoder = new OpusEncoder(StreamFormat.SAMPLE_RATE, 1, OpusApplication.OPUS_APPLICATION_AUDIO);
		} catch (OpusException e) {
			throw new IllegalStateException("Cannot create Opus encoder", e);
		}
		encoder.setBitrate(StreamFormat.BITRATE);
		encoder.setComplexity(StreamFormat.COMPLEXITY);
		encoder.setSignalType(OpusSignal.OPUS_SIGNAL_MUSIC);
	}

	public byte[] encode(short[] frame) {
		if (frame.length != StreamFormat.FRAME_SAMPLES) {
			throw new IllegalArgumentException("Expected " + StreamFormat.FRAME_SAMPLES + " samples, got " + frame.length);
		}
		try {
			int length = encoder.encode(frame, 0, StreamFormat.FRAME_SAMPLES, out, 0, out.length);
			return Arrays.copyOf(out, length);
		} catch (OpusException e) {
			throw new IllegalStateException("Opus encoding failed", e);
		}
	}
}
