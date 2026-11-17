package com.ravecraft.mod.core.audio.decode;

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

	private SampleBuffer nextFrame() throws IOException {
		try {
			Header header = bitstream.readFrame();
			if (header == null) {
				return null;
			}
			try {
				return (SampleBuffer) decoder.decodeFrame(header, bitstream);
			} finally {
				bitstream.closeFrame();
			}
		} catch (BitstreamException | DecoderException | RuntimeException e) {
			// JLayer throws unchecked exceptions (array bounds...) on some corrupt files.
			throw new IOException("Corrupt MP3 data", e);
		}
	}

	private void keep(SampleBuffer samples) {
		short[] data = samples.getBuffer();
		int length = samples.getBufferLength();
		byte[] pcm = scratch(length * 2);
		for (int i = 0; i < length; i++) {
			pcm[i * 2] = (byte) data[i];
			pcm[i * 2 + 1] = (byte) (data[i] >> 8);
		}
		setPending(pcm, length * 2);
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
