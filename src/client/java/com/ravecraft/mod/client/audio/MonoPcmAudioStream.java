package com.ravecraft.mod.client.audio;

import com.ravecraft.mod.core.audio.Pcm16;
import com.ravecraft.mod.core.audio.decode.PcmDecoder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import javax.sound.sampled.AudioFormat;
import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.BufferUtils;

/** Feeds a {@link PcmDecoder} to the sound engine as 16-bit mono, the only layout OpenAL spatialises. */
final class MonoPcmAudioStream implements AudioStream {
	private final PcmDecoder decoder;
	private final AudioFormat format;
	private final byte[] scratch = new byte[16384];
	private final ByteArrayOutputStream chunk = new ByteArrayOutputStream();

	MonoPcmAudioStream(PcmDecoder decoder) {
		this.decoder = decoder;
		this.format = new AudioFormat(decoder.format().sampleRate(), 16, 1, true, false);
	}

	@Override
	public AudioFormat getFormat() {
		return format;
	}

	@Override
	public ByteBuffer read(int expectedSize) throws IOException {
		int channels = decoder.format().channels();
		int wanted = expectedSize * channels;
		chunk.reset();
		while (chunk.size() < wanted) {
			int read = decoder.read(scratch);
			if (read < 0) {
				break;
			}
			chunk.write(scratch, 0, read);
		}
		return direct(Pcm16.downmixToMono(chunk.toByteArray(), chunk.size(), channels));
	}

	/** Copies samples into the native buffer the sound engine expects; an empty buffer ends the stream. */
	static ByteBuffer direct(byte[] samples) {
		ByteBuffer buffer = BufferUtils.createByteBuffer(samples.length);
		buffer.put(samples).flip();
		return buffer;
	}

	@Override
	public void close() throws IOException {
		decoder.close();
	}
}
