package com.ravecraft.mod.core.audio.decode;

import com.ravecraft.mod.core.audio.Pcm16;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.DecoderException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

/** MP3 through JLayer, frame by frame. The format is taken from the first frame. */
final class Mp3Decoder extends BlockPcmDecoder {
	private final Bitstream bitstream;
	private final Decoder decoder = new Decoder();
	private final PcmFormat format;

	/**
	 * Opens {@code file} and decodes its first frame to learn the format.
	 *
	 * @param file the MP3 file
	 * @throws IOException when the file cannot be read or holds no MP3 frame
	 */
	Mp3Decoder(Path file) throws IOException {
		bitstream = new Bitstream(new BufferedInputStream(Files.newInputStream(file)));
		try {
			SampleBuffer first = nextFrame();
			if (first == null) {
				throw new IOException("No MP3 frame in " + file.getFileName());
			}
			format = new PcmFormat(first.getSampleFrequency(), first.getChannelCount());
			keep(first);
		} catch (IOException e) {
			close();
			throw e;
		}
	}

	@Override
	public PcmFormat format() {
		return format;
	}

	@Override
	protected boolean decodeNextBlock() throws IOException {
		SampleBuffer samples = nextFrame();
		if (samples == null) {
			return false;
		}
		keep(samples);
		return true;
	}

	/**
	 * Decodes the next frame.
	 *
	 * @return its samples, or null at the end of the stream
	 * @throws IOException when the data is corrupt
	 */
	private SampleBuffer nextFrame() throws IOException {
		try {
			Header header = bitstream.readFrame();
			return header == null ? null : decode(header);
		} catch (BitstreamException | DecoderException | RuntimeException e) {
			// JLayer throws unchecked exceptions (array bounds...) on some corrupt files.
			throw new IOException("Corrupt MP3 data", e);
		}
	}

	private SampleBuffer decode(Header header) throws DecoderException {
		try {
			return (SampleBuffer) decoder.decodeFrame(header, bitstream);
		} finally {
			bitstream.closeFrame();
		}
	}

	private void keep(SampleBuffer samples) {
		short[] data = samples.getBuffer();
		int length = samples.getBufferLength();
		byte[] pcm = scratch(length * Pcm16.SAMPLE_BYTES);
		for (int i = 0; i < length; i++) {
			Pcm16.put(pcm, i * Pcm16.SAMPLE_BYTES, data[i]);
		}
		setPending(pcm, length * Pcm16.SAMPLE_BYTES);
	}

	@Override
	public void close() throws IOException {
		try {
			bitstream.close();
		} catch (BitstreamException e) {
			throw new IOException(e);
		}
	}
}
