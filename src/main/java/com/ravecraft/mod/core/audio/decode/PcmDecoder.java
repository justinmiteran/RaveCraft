package com.ravecraft.mod.core.audio.decode;

import java.io.Closeable;
import java.io.IOException;

/** Streams an audio file as 16-bit signed little-endian interleaved PCM. */
public interface PcmDecoder extends Closeable {
	/**
	 * The shape of the PCM {@link #read} produces.
	 *
	 * @return the PCM format
	 */
	PcmFormat format();

	/**
	 * Fills {@code buffer} with whole frames.
	 *
	 * @param buffer receives the PCM bytes
	 * @return the number of bytes written, or {@code -1} at the end of the stream
	 */
	int read(byte[] buffer) throws IOException;
}
