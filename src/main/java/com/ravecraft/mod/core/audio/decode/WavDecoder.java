package com.ravecraft.mod.core.audio.decode;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

/** WAV through {@code javax.sound.sampled}, converted to 16-bit signed little-endian whatever the source depth. */
final class WavDecoder implements PcmDecoder {
	private final AudioInputStream stream;
	private final PcmFormat format;

	WavDecoder(Path file) throws IOException {
		InputStream raw = new BufferedInputStream(Files.newInputStream(file));
		try {
			AudioInputStream source = AudioSystem.getAudioInputStream(raw);
			AudioFormat in = source.getFormat();
			AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, in.getSampleRate(), 16,
					in.getChannels(), in.getChannels() * 2, in.getSampleRate(), false);
			this.stream = AudioSystem.getAudioInputStream(target, source);
			this.format = new PcmFormat(Math.round(in.getSampleRate()), in.getChannels());
		} catch (UnsupportedAudioFileException | IllegalArgumentException e) {
			raw.close();
			throw new IOException("Unsupported WAV file: " + file.getFileName(), e);
		}
	}

	@Override
	public PcmFormat format() {
		return format;
	}

	@Override
	public int read(byte[] buffer) throws IOException {
		int room = buffer.length - buffer.length % format.frameBytes();
		int read;
		do {
			read = stream.read(buffer, 0, room);
		} while (read == 0);
		return read;
	}

	@Override
	public void close() throws IOException {
		stream.close();
	}
}
