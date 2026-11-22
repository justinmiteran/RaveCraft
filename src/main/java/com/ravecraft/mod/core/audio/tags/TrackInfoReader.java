package com.ravecraft.mod.core.audio.tags;

import com.ravecraft.mod.core.audio.Bytes;
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
	private static final int ID3_HEADER = 10;
	private static final int ID3_VERSION_AT = 3;
	private static final int ID3_FLAGS_AT = 5;
	private static final int ID3_SIZE_AT = 6;
	private static final int ID3_EXTENDED_FLAG = 0x40;
	private static final int ID3_FOOTER_FLAG = 0x10;
	private static final int ID3V1_LENGTH = 128;
	private static final int TECH_FIELDS = 3;
	private static final int TECH_RATE = 1;
	private static final int TECH_CHANNELS = 2;

	private TrackInfoReader() {
	}

	/**
	 * Reads a track's tags, stream facts and duration.
	 *
	 * @param file the audio file
	 * @return what could be read; unknown fields stay empty or zero
	 */
	public static TrackInfo read(Path file) {
		String ext = "";
		try {
			String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
			int dot = name.lastIndexOf('.');
			ext = dot < 0 ? "" : name.substring(dot + 1);
			long durationMs = TrackDuration.of(file).orElse(-1);
			TrackInfo info = switch (ext) {
				case "mp3" -> mp3(file);
				case "flac" -> container(file, "FLAC");
				case "wav" -> container(file, "WAV");
				case "ogg", "oga", "opus" -> container(file, "Ogg");
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

	/**
	 * Reads an MP3: its ID3v2 tag, the first frame header, then ID3v1 for whatever is still missing.
	 *
	 * @param file the MP3 file
	 * @return the tags and stream facts, without duration
	 */
	private static TrackInfo mp3(Path file) {
		Map<String, String> tags = new HashMap<>();
		Optional<MpegHeader> stream = Optional.empty();
		try (RandomAccessFile f = new RandomAccessFile(file.toFile(), "r")) {
			long audioStart = id3v2(f, tags);
			stream = findHeader(ByteBuffer.wrap(readAt(f, audioStart, WINDOW)));
			if (f.length() >= ID3V1_LENGTH) {
				Id3Tags.readV1(ByteBuffer.wrap(readAt(f, f.length() - ID3V1_LENGTH, ID3V1_LENGTH)), tags);
			}
		} catch (IOException | RuntimeException ignored) {
			// Return what was gathered.
		}
		return new TrackInfo(tags.get(Id3Tags.TITLE), tags.get(Id3Tags.ARTIST), tags.get(Id3Tags.ALBUM),
				tags.get(Id3Tags.YEAR), tags.get(Id3Tags.GENRE), "MP3", stream.map(MpegHeader::kbps).orElse(0),
				stream.map(MpegHeader::rate).orElse(0), stream.map(MpegHeader::channels).orElse(0), -1);
	}

	/**
	 * Reads the ID3v2 tag at the start of {@code f} into {@code tags}, when there is one.
	 *
	 * @param f the MP3 file
	 * @param tags the tags read so far
	 * @return the offset of the audio after the tag, or 0 without a tag
	 * @throws IOException when the file cannot be read
	 */
	private static long id3v2(RandomAccessFile f, Map<String, String> tags) throws IOException {
		ByteBuffer head = ByteBuffer.wrap(readAt(f, 0, ID3_HEADER));
		if (head.limit() != ID3_HEADER || !Bytes.fourCc(head, 0).startsWith("ID3")) {
			return 0;
		}
		int version = head.get(ID3_VERSION_AT);
		int flags = head.get(ID3_FLAGS_AT);
		int size = Bytes.syncsafe(head, ID3_SIZE_AT);
		ByteBuffer tag = ByteBuffer.wrap(readAt(f, ID3_HEADER, Math.min(size, BUDGET)));
		readId3v2Body(tag, version, (flags & ID3_EXTENDED_FLAG) != 0, tags);
		return (long) ID3_HEADER + size + ((flags & ID3_FOOTER_FLAG) != 0 ? ID3_HEADER : 0);
	}

	private static void readId3v2Body(ByteBuffer tag, int version, boolean extended, Map<String, String> tags) {
		try {
			if (extended) {
				Id3Tags.skipExtendedHeader(tag, version);
			}
			Id3Tags.readV2(tag, version, tags);
		} catch (RuntimeException ignored) {
			// A broken tag keeps what was read before it.
		}
	}

	/** FLAC, WAV and Ogg: {@code format} is the default name, replaced by the codec for Ogg. */
	private static TrackInfo container(Path file, String format) {
		Map<String, String> tags = new HashMap<>();
		int[] tech = new int[TECH_FIELDS];
		String name = format;
		try (RandomAccessFile f = new RandomAccessFile(file.toFile(), "r")) {
			switch (format) {
				case "FLAC" -> ContainerTags.flac(f, tags, tech);
				case "WAV" -> ContainerTags.wav(f, tags, tech);
				default -> name = ContainerTags.ogg(f, tags, tech);
			}
		} catch (IOException | RuntimeException ignored) {
			// Return what was gathered.
		}
		return new TrackInfo(tags.get(Id3Tags.TITLE), tags.get(Id3Tags.ARTIST), tags.get(Id3Tags.ALBUM),
				tags.get(Id3Tags.YEAR), tags.get(Id3Tags.GENRE), name, tech[0], tech[TECH_RATE], tech[TECH_CHANNELS],
				-1);
	}

	private static Optional<MpegHeader> findHeader(ByteBuffer b) {
		for (int i = 0; i + Integer.BYTES <= b.limit(); i++) {
			Optional<MpegHeader> h = TrackDuration.mpegHeader(b.getInt(i));
			if (h.isPresent()) {
				return h;
			}
		}
		return Optional.empty();
	}
}
