package com.ravecraft.mod.client.audio.stream;

import com.ravecraft.mod.core.audio.stream.StreamFormat;
import com.ravecraft.mod.core.audio.stream.StreamPlayout;
import java.nio.ByteBuffer;
import javax.sound.sampled.AudioFormat;
import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.BufferUtils;

/**
 * A relayed stream as seen by the sound engine. Each read returns 100 ms whatever the requested size: the engine
 * keeps 4 buffers queued, so latency stays around 400 ms past the jitter buffer, and the source never runs dry.
 */
final class NetworkAudioStream implements AudioStream {
	private static final AudioFormat FORMAT = new AudioFormat(StreamFormat.SAMPLE_RATE, 16, 1, true, false);

	private final StreamPlayout playout;

	NetworkAudioStream(StreamPlayout playout) {
		this.playout = playout;
	}

	@Override
	public AudioFormat getFormat() {
		return FORMAT;
	}

	@Override
	public ByteBuffer read(int expectedSize) {
		byte[] block = playout.nextBlock();
		ByteBuffer buffer = BufferUtils.createByteBuffer(block.length);
		buffer.put(block).flip();
		return buffer;
	}

	@Override
	public void close() {
	}
}
