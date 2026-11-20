package com.ravecraft.mod.core.audio.tags;

import com.ravecraft.mod.core.audio.decode.TrackDuration;
import com.ravecraft.mod.core.audio.decode.TrackDuration.MpegHeader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Reads a track's tags and stream facts from its file header. Never throws: what cannot be read stays unknown. */
public final class TrackInfoReader {
	/** The most bytes read for tags (the MPEG frame search and ID3v1 come on top of it). */
	public static final int BUDGET = 1 << 20;
	private static final int WINDOW = 64 * 1024;

	private TrackInfoReader() {
	}

	public static TrackInfo read(Path file) {
		String ext = "";
		try {
			String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
			int dot = name.lastIndexOf('.');
			ext = dot < 0 ? "" : name.substring(dot + 1);
			long durationMs = TrackDuration.of(file).orElse(-1);
			TrackInfo info = switch (ext) {
				case "mp3" -> mp3(file);
				case "flac" -> TrackInfo.empty("FLAC");
				case "wav" -> TrackInfo.empty("WAV");
				case "ogg", "oga", "opus" -> TrackInfo.empty("Ogg");
				default -> TrackInfo.empty(ext.toUpperCase(Locale.ROOT));
			};
			return withDuration(info, durationMs);
		} catch (RuntimeException e) {
			return TrackInfo.empty(ext.toUpperCase(Locale.ROOT));
		}
	}

	private static TrackInfo withDuration(TrackInfo i, long durationMs) {
		return new TrackInfo(i.title(), i.artist(), i.album(), i.year(), i.genre(), i.format(), i.bitrateKbps(),
				i.sampleRateHz(), i.channels(), durationMs);
	}

	private static byte[] readAt(RandomAccessFile f, long offset, long max) throws IOException {
		byte[] data = new byte[(int) Math.max(0, Math.min(f.length() - offset, max))];
		f.seek(offset);
		f.readFully(data);
		return data;
	}

	private static TrackInfo mp3(Path file) {
		Map<String, String> tags = new HashMap<>();
		Optional<MpegHeader> stream = Optional.empty();
		try (RandomAccessFile f = new RandomAccessFile(file.toFile(), "r")) {
			long audioStart = 0;
			ByteBuffer head = ByteBuffer.wrap(readAt(f, 0, 10));
			if (head.limit() == 10 && head.get(0) == 'I' && head.get(1) == 'D' && head.get(2) == '3') {
				int version = head.get(3);
				int size = Id3Tags.syncsafe(head, 6);
				audioStart = 10L + size + ((head.get(5) & 0x10) != 0 ? 10 : 0);
				try {
					ByteBuffer tag = ByteBuffer.wrap(readAt(f, 10, Math.min(size, BUDGET)));
					if ((head.get(5) & 0x40) != 0) {
						Id3Tags.skipExtendedHeader(tag, version);
					}
					Id3Tags.readV2(tag, version, tags);
				} catch (RuntimeException e) {
					// A broken tag keeps what was read before it.
				}
			}
			stream = findHeader(ByteBuffer.wrap(readAt(f, audioStart, WINDOW)));
			if (f.length() >= 128) {
				Id3Tags.readV1(ByteBuffer.wrap(readAt(f, f.length() - 128, 128)), tags);
			}
		} catch (IOException | RuntimeException e) {
			// Return what was gathered.
		}
		return new TrackInfo(tags.get(Id3Tags.TITLE), tags.get(Id3Tags.ARTIST), tags.get(Id3Tags.ALBUM),
				tags.get(Id3Tags.YEAR), tags.get(Id3Tags.GENRE), "MP3", stream.map(MpegHeader::kbps).orElse(0),
				stream.map(MpegHeader::rate).orElse(0), stream.map(MpegHeader::channels).orElse(0), -1);
	}

	private static Optional<MpegHeader> findHeader(ByteBuffer b) {
		for (int i = 0; i + 4 <= b.limit(); i++) {
			Optional<MpegHeader> h = TrackDuration.mpegHeader(b.getInt(i));
			if (h.isPresent()) {
				return h;
			}
		}
		return Optional.empty();
	}
}
