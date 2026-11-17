package com.ravecraft.mod.core.audio.decode;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jflac.ChannelData;
import org.jflac.frame.Frame;
import org.jflac.metadata.StreamInfo;

/** FLAC through jFLAC, frame by frame; any bit depth is scaled to 16-bit. */
final class FlacDecoder extends BlockPcmDecoder {
	private final InputStream input;
	private final org.jflac.FLACDecoder decoder;
	private final PcmFormat format;
	private final int bitsPerSample;

	FlacDecoder(Path file) throws IOException {
		input = new BufferedInputStream(Files.newInputStream(file));
		try {
			decoder = new org.jflac.FLACDecoder(input);
			decoder.readMetadata();
			StreamInfo info = decoder.getStreamInfo();
			if (info == null) {
				throw new IOException("No FLAC stream info");
			}
			format = new PcmFormat(info.getSampleRate(), info.getChannels());
			bitsPerSample = info.getBitsPerSample();
		} catch (IOException | RuntimeException e) {
			input.close();
			throw e instanceof IOException io ? io : new IOException("Corrupt FLAC file: " + file.getFileName(), e);
		}
	}

	@Override
	public PcmFormat format() {
		return format;
	}

	@Override
	protected boolean decodeNextBlock() throws IOException {
		Frame frame;
		try {
			frame = decoder.readNextFrame();
		} catch (RuntimeException e) {
			throw new IOException("Corrupt FLAC frame", e);
		}
		if (frame == null) {
			return false;
		}
		ChannelData[] channels = decoder.getChannelData();
		int samples = frame.header.blockSize;
		int count = format.channels();
		byte[] pcm = scratch(samples * format.frameBytes());
		int at = 0;
		for (int i = 0; i < samples; i++) {
			for (int channel = 0; channel < count; channel++) {
				int sample = to16Bit(channels[channel].getOutput()[i]);
				pcm[at++] = (byte) sample;
				pcm[at++] = (byte) (sample >> 8);
			}
		}
		setPending(pcm, at);
		return true;
	}

	private int to16Bit(int sample) {
		return bitsPerSample >= 16 ? sample >> (bitsPerSample - 16) : sample << (16 - bitsPerSample);
	}

	@Override
	public void close() throws IOException {
		input.close();
	}
}
