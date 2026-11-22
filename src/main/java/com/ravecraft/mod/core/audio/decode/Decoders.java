package com.ravecraft.mod.core.audio.decode;

import com.ravecraft.mod.core.library.AudioFiles;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/** Picks the decoder for a file from its extension. */
public final class Decoders {
	private Decoders() {
	}

	/**
	 * Opens a decoder for {@code file}. OGG returns an empty optional: the client decodes it with Minecraft's own
	 * Vorbis stream.
	 *
	 * @param file the audio file
	 * @return the decoder, or empty for OGG
	 * @throws IOException if the file cannot be read or its extension is not an audio format
	 */
	public static Optional<PcmDecoder> open(Path file) throws IOException {
		return switch (AudioFiles.extension(file)) {
			case "wav" -> Optional.of(new WavDecoder(file));
			case "flac" -> Optional.of(new FlacDecoder(file));
			case "mp3" -> Optional.of(new Mp3Decoder(file));
			case "ogg", "oga" -> Optional.empty();
			default -> throw new IOException("Not an audio file: " + file.getFileName());
		};
	}
}
