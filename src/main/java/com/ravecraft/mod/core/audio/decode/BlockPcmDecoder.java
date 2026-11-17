package com.ravecraft.mod.core.audio.decode;

import java.io.IOException;

/**
 * Base for codecs that decode one block (an MP3 or FLAC frame) at a time: keeps the leftover of the last block so
 * that {@link #read} hands out whole PCM frames whatever the caller's buffer size.
 */
abstract class BlockPcmDecoder implements PcmDecoder {
	private byte[] pending = new byte[0];
	private int pendingStart;
	private int pendingEnd;

	/** Decodes the next block and passes it to {@link #setPending}; returns false at the end of the stream. */
	protected abstract boolean decodeNextBlock() throws IOException;

	protected final void setPending(byte[] pcm, int length) {
		pending = pcm;
		pendingStart = 0;
		pendingEnd = length;
	}

	/** A scratch array of at least {@code size} bytes, reusing the pending one when it is no longer needed. */
	protected final byte[] scratch(int size) {
		return pending.length >= size && pendingStart >= pendingEnd ? pending : new byte[size];
	}

	@Override
	public final int read(byte[] buffer) throws IOException {
		int frameBytes = format().frameBytes();
		int room = buffer.length - buffer.length % frameBytes;
		if (room == 0) {
			throw new IllegalArgumentException("Buffer smaller than one frame: " + buffer.length);
		}
		while (pendingStart >= pendingEnd) {
			if (!decodeNextBlock()) {
				return -1;
			}
		}
		int count = Math.min(room, pendingEnd - pendingStart);
		System.arraycopy(pending, pendingStart, buffer, 0, count);
		pendingStart += count;
		return count;
	}
}
