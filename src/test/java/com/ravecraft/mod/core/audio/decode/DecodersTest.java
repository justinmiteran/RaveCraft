package com.ravecraft.mod.core.audio.decode;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.Random;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import net.sourceforge.javaflacencoder.FLACEncoder;
import net.sourceforge.javaflacencoder.FLACFileOutputStream;
import net.sourceforge.javaflacencoder.StreamConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DecodersTest {
	private static final int RATE = 44100;
	private static final int FRAMES = RATE / 2;

	@TempDir
	Path dir;

	/** 0.5 s of a 440 Hz sine, stereo 16-bit little-endian, the right channel at half volume. */
	private static byte[] sineStereo16() {
		byte[] pcm = new byte[FRAMES * 4];
		for (int i = 0; i < FRAMES; i++) {
			short left = (short) (Math.sin(2 * Math.PI * 440 * i / RATE) * 20000);
			short right = (short) (left / 2);
			pcm[i * 4] = (byte) left;
			pcm[i * 4 + 1] = (byte) (left >> 8);
			pcm[i * 4 + 2] = (byte) right;
			pcm[i * 4 + 3] = (byte) (right >> 8);
		}
		return pcm;
	}

	private Path writeWav(String name, byte[] pcm, AudioFormat format) throws IOException {
		Path file = dir.resolve(name);
		long frames = pcm.length / format.getFrameSize();
		try (AudioInputStream in = new AudioInputStream(new ByteArrayInputStream(pcm), format, frames)) {
			AudioSystem.write(in, AudioFileFormat.Type.WAVE, file.toFile());
		}
		return file;
	}

	/** Encodes stereo 16-bit PCM to FLAC; the encoder's file helper leaks handles, so the stream is closed here. */
	private Path writeFlac(String name, byte[] pcm) throws IOException {
		Path file = dir.resolve(name);
		int[] samples = new int[pcm.length / 2];
		for (int i = 0; i < samples.length; i++) {
			samples[i] = (short) ((pcm[i * 2] & 0xFF) | (pcm[i * 2 + 1] << 8));
		}
		FLACEncoder encoder = new FLACEncoder();
		StreamConfiguration config = new StreamConfiguration();
		config.setChannelCount(2);
		config.setSampleRate(RATE);
		config.setBitsPerSample(16);
		encoder.setStreamConfiguration(config);
		try (FLACFileOutputStream out = new FLACFileOutputStream(file.toFile())) {
			encoder.setOutputStream(out);
			encoder.openFLACStream();
			encoder.addSamples(samples, samples.length / 2);
			encoder.encodeSamples(samples.length / 2, true);
		}
		return file;
	}

	private static byte[] decodeAll(PcmDecoder decoder) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[4096];
		int read;
		while ((read = decoder.read(buffer)) != -1) {
			out.write(buffer, 0, read);
		}
		return out.toByteArray();
	}

	@Test
	void stereoWavDecodesToItsFrames() throws IOException {
		Path wav = writeWav("sine.wav", sineStereo16(), new AudioFormat(RATE, 16, 2, true, false));
		try (PcmDecoder decoder = Decoders.open(wav).orElseThrow()) {
			assertEquals(new PcmFormat(RATE, 2), decoder.format());
			int frames = decodeAll(decoder).length / 4;
			assertTrue(Math.abs(frames - FRAMES) <= 1, "frames: " + frames);
		}
	}

	@Test
	void eightBitMonoWavIsWidenedTo16Bit() throws IOException {
		byte[] pcm = new byte[11025];
		for (int i = 0; i < pcm.length; i++) {
			pcm[i] = (byte) (Math.sin(i / 10.0) * 100);
		}
		Path wav = writeWav("low.wav", pcm, new AudioFormat(22050, 8, 1, true, false));
		try (PcmDecoder decoder = Decoders.open(wav).orElseThrow()) {
			assertEquals(new PcmFormat(22050, 1), decoder.format());
			assertEquals(pcm.length * 2, decodeAll(decoder).length);
		}
	}

	@Test
	void flacDecodesToTheSourceSamples() throws IOException {
		byte[] source = sineStereo16();
		Path flac = writeFlac("sine.flac", source);
		try (PcmDecoder decoder = Decoders.open(flac).orElseThrow()) {
			assertEquals(new PcmFormat(RATE, 2), decoder.format());
			byte[] decoded = decodeAll(decoder);
			assertArrayEquals(Arrays.copyOf(source, 2000), Arrays.copyOf(decoded, 2000));
		}
	}

	@Test
	void corruptMp3EndsOrFailsCleanly() throws IOException {
		byte[] noise = new byte[64 * 1024];
		new Random(42).nextBytes(noise);
		Path mp3 = Files.write(dir.resolve("noise.mp3"), noise);
		try {
			decodeAllOf(Decoders.open(mp3));
		} catch (IOException expected) {
			// a clean failure is fine too
		}
	}

	private static void decodeAllOf(Optional<PcmDecoder> opened) throws IOException {
		try (PcmDecoder decoder = opened.orElseThrow()) {
			decodeAll(decoder);
		}
	}

	@Test
	void oggIsLeftToTheClient() throws IOException {
		assertEquals(Optional.empty(), Decoders.open(Files.createFile(dir.resolve("a.ogg"))));
	}

	@Test
	void unknownExtensionIsRejected() throws IOException {
		Path text = Files.createFile(dir.resolve("x.txt"));
		assertThrows(IOException.class, () -> Decoders.open(text));
	}
}
