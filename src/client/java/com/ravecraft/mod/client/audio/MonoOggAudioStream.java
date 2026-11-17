package com.ravecraft.mod.client.audio;

import com.ravecraft.mod.core.audio.Pcm16;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sound.sampled.AudioFormat;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.JOrbisAudioStream;

/** OGG Vorbis through Minecraft's own decoder, downmixed to mono when the file has several channels. */
final class MonoOggAudioStream implements AudioStream {
	private final JOrbisAudioStream vorbis;
	private final int channels;
	private final AudioFormat format;

	MonoOggAudioStream(Path file) throws IOException {
		InputStream input = new BufferedInputStream(Files.newInputStream(file));
		try {
			vorbis = new JOrbisAudioStream(input);
		} catch (IOException | RuntimeException e) {
			input.close();
			throw e instanceof IOException io ? io : new IOException("Corrupt OGG file: " + file.getFileName(), e);
		}
		AudioFormat source = vorbis.getFormat();
		channels = source.getChannels();
		format = new AudioFormat(source.getSampleRate(), 16, 1, true, false);
	}

	@Override
	public AudioFormat getFormat() {
		return format;
	}

	@Override
	public ByteBuffer read(int expectedSize) throws IOException {
		ByteBuffer decoded = vorbis.read(expectedSize * channels);
		if (channels == 1) {
			return decoded;
		}
		byte[] samples = new byte[decoded.remaining()];
		decoded.get(samples);
		return MonoPcmAudioStream.direct(Pcm16.downmixToMono(samples, samples.length, channels));
	}

	@Override
	public void close() throws IOException {
		vorbis.close();
	}
}
