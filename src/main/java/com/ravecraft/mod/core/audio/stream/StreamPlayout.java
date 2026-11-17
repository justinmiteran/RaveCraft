package com.ravecraft.mod.core.audio.stream;

import com.ravecraft.mod.core.audio.opus.OpusMonoDecoder;
import com.ravecraft.mod.core.audio.stream.JitterBuffer.Audio;
import com.ravecraft.mod.core.audio.stream.JitterBuffer.Lost;
import com.ravecraft.mod.core.audio.stream.JitterBuffer.Slot;
import java.util.List;

/**
 * The receiving end of one stream: chunks go in from the network, 100 ms PCM blocks come out for the sound engine.
 * A block is never empty (silence while buffering), so the engine never runs dry and ends the sound. Thread-safe.
 */
public final class StreamPlayout {
	private final JitterBuffer buffer = new JitterBuffer(StreamFormat.JITTER_TARGET_FRAMES, StreamFormat.MAX_AHEAD_FRAMES);
	private final OpusMonoDecoder decoder = new OpusMonoDecoder();
	private long lastOfferMillis;
	private boolean finished;

	public StreamPlayout(long nowMillis) {
		this.lastOfferMillis = nowMillis;
	}

	/** Adds chunk {@code seq}; its frames are numbered from {@code seq * FRAMES_PER_CHUNK}. */
	public synchronized void offer(int seq, List<byte[]> frames, long nowMillis) {
		buffer.offer((long) seq * StreamFormat.FRAMES_PER_CHUNK, frames);
		lastOfferMillis = nowMillis;
	}

	/** Exactly {@link StreamFormat#CHUNK_SAMPLES} samples of 16-bit little-endian mono PCM. */
	public synchronized byte[] nextBlock() {
		byte[] out = new byte[StreamFormat.CHUNK_SAMPLES * 2];
		for (int f = 0; f < StreamFormat.FRAMES_PER_CHUNK; f++) {
			Slot slot = buffer.poll();
			short[] pcm;
			if (slot instanceof Audio audio) {
				pcm = decoder.decode(audio.frame());
			} else if (slot instanceof Lost) {
				pcm = decoder.decode(null);
			} else {
				continue;
			}
			int base = f * StreamFormat.FRAME_SAMPLES * 2;
			for (int i = 0; i < pcm.length; i++) {
				out[base + i * 2] = (byte) pcm[i];
				out[base + i * 2 + 1] = (byte) (pcm[i] >> 8);
			}
		}
		return out;
	}

	/** The stream ended normally: play out the buffered tail, then report {@link #isDrained()}. */
	public synchronized void finish() {
		finished = true;
		buffer.drain();
	}

	/** True once {@link #finish()} was called and everything buffered has been played. */
	public synchronized boolean isDrained() {
		return finished && buffer.bufferedFrames() == 0;
	}

	/** True when nothing arrived during the last {@code millis}. */
	public synchronized boolean idleFor(long millis, long nowMillis) {
		return nowMillis - lastOfferMillis >= millis;
	}
}
