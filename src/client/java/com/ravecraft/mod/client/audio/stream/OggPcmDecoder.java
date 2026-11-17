package com.ravecraft.mod.client.audio.stream;

import com.ravecraft.mod.core.audio.decode.PcmDecoder;
import com.ravecraft.mod.core.audio.decode.PcmFormat;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sound.sampled.AudioFormat;
import net.minecraft.client.sounds.JOrbisAudioStream;

/** OGG Vorbis through Minecraft's own decoder, exposed as a {@link PcmDecoder} (16-bit little-endian). */
final class OggPcmDecoder implements PcmDecoder {
	private final JOrbisAudioStream vorbis;
	private final PcmFormat format;
	private ShortBuffer pending = ShortBuffer.allocate(0);

	OggPcmDecoder(Path file) throws IOException {
		InputStream input = new BufferedInputStream(Files.newInputStream(file));
		try {
			vorbis = new JOrbisAudioStream(input);
		} catch (IOException | RuntimeException e) {
			input.close();
			throw e instanceof IOException io ? io : new IOException("Corrupt OGG file: " + file.getFileName(), e);
		}
		AudioFormat source = vorbis.getFormat();
		format = new PcmFormat((int) source.getSampleRate(), source.getChannels());
	}

	@Override
	public PcmFormat format() {
		return format;
	}

	@Override
	public int read(byte[] buffer) throws IOException {
		if (!pending.hasRemaining()) {
			ByteBuffer decoded = vorbis.read(buffer.length);
			if (!decoded.hasRemaining()) {
				return -1;
			}
			// The decoder writes samples in native byte order.
			pending = decoded.order(ByteOrder.nativeOrder()).asShortBuffer();
		}
		int frameSamples = format.channels();
		int samples = Math.min(pending.remaining(), buffer.length / 2);
		samples -= samples % frameSamples;
		for (int i = 0; i < samples; i++) {
			short sample = pending.get();
			buffer[i * 2] = (byte) sample;
			buffer[i * 2 + 1] = (byte) (sample >> 8);
		}
		return samples * 2;
	}

	@Override
	public void close() throws IOException {
		vorbis.close();
	}
}
