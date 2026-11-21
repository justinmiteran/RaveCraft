package com.ravecraft.mod.core.audio.decode;

import static com.ravecraft.mod.core.audio.Bytes.FOUR_CC;
import static com.ravecraft.mod.core.audio.Bytes.fourCc;
import static com.ravecraft.mod.core.audio.Bytes.u16;
import static com.ravecraft.mod.core.audio.Bytes.u32;
import static com.ravecraft.mod.core.audio.Bytes.u8;

import com.ravecraft.mod.core.audio.Bytes;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Reads a track's length from its file header, without decoding any audio. Reads at most the first 64 KiB (and,
 * for Ogg, the last 64 KiB). Never throws: anything unreadable or malformed is unknown.
 */
public final class TrackDuration {
	private static final long UNKNOWN = -1;
	private static final int WINDOW = 64 * 1024;
	private static final long MILLIS_PER_SECOND = 1000L;
	private static final int BITS_PER_BYTE = 8;

	// RIFF/WAVE: "RIFF" size "WAVE", then chunks of id (4) + size (4) + body, padded to even length.
	private static final int RIFF_FORM_AT = 8;
	private static final int RIFF_FIRST_CHUNK_AT = 12;
	private static final int CHUNK_HEADER = 8;
	private static final int CHUNK_SIZE_AT = 4;
	private static final int FMT_BYTE_RATE_AT = 16;

	// FLAC: "fLaC", then the STREAMINFO block; rate (20 bits) and total samples (36 bits) share one long.
	private static final int FLAC_STREAMINFO_END = 42;
	private static final int FLAC_BLOCK_TYPE_AT = 4;
	private static final int FLAC_BLOCK_TYPE_MASK = 0x7F;
	private static final int FLAC_RATE_SAMPLES_AT = 18;
	private static final int FLAC_RATE_SHIFT = 44;
	private static final long FLAC_SAMPLES_MASK = 0xFFFFFFFFFL;

	// ID3v2 header in front of an MP3: "ID3", version (2), flags, syncsafe size; a footer repeats the header.
	private static final int ID3_HEADER = 10;
	private static final int ID3_FLAGS_AT = 5;
	private static final int ID3_SIZE_AT = 6;
	private static final int ID3_FOOTER_FLAG = 0x10;

	// MPEG audio frame header (32 bits).
	private static final int MPEG_SYNC = 0xFFE00000;
	private static final int MPEG_VERSION_SHIFT = 19;
	private static final int MPEG_LAYER_SHIFT = 17;
	private static final int MPEG_BITRATE_SHIFT = 12;
	private static final int MPEG_RATE_SHIFT = 10;
	private static final int MPEG_MODE_SHIFT = 6;
	private static final int TWO_BITS = 3;
	private static final int FOUR_BITS = 15;
	private static final int VERSION_1 = 3;
	private static final int VERSION_2 = 2;
	private static final int VERSION_RESERVED = 1;
	private static final int LAYER_3 = 1;
	private static final int BITRATE_FREE = 0;
	private static final int BITRATE_BAD = 15;
	private static final int RATE_RESERVED = 3;
	private static final int MODE_MONO = 3;
	private static final int MPEG2_RATE_DIVISOR = 2;
	private static final int MPEG25_RATE_DIVISOR = 4;
	private static final int[] MPEG1_L3_KBPS = {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320};
	private static final int[] MPEG2_L3_KBPS = {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160};
	private static final int[] MPEG1_RATES = {44100, 48000, 32000};

	// Layer III frames: samples per frame and side-information size, which the Xing/Info tag follows.
	private static final int MPEG1_SAMPLES = 1152;
	private static final int MPEG2_SAMPLES = 576;
	private static final int MPEG1_SIDE_MONO = 17;
	private static final int MPEG1_SIDE_STEREO = 32;
	private static final int MPEG2_SIDE_MONO = 9;
	private static final int MPEG2_SIDE_STEREO = 17;
	private static final int XING_TAG_BYTES = 12;
	private static final int XING_FLAGS_AT = 4;
	private static final int XING_FRAMES_AT = 8;
	private static final int XING_HAS_FRAMES = 1;
	private static final int VBRI_AT = 32;
	private static final int VBRI_TAG_BYTES = 18;
	private static final int VBRI_FRAMES_AT = 14;

	// Ogg: page header (27 bytes + segment table), then the codec's identification packet.
	private static final int OGG_PAGE_HEADER = 27;
	private static final int OGG_SEGMENTS_AT = 26;
	private static final int OGG_GRANULE_AT = 6;
	private static final int VORBIS_ID_BYTES = 16;
	private static final int VORBIS_PACKET_TYPE = 1;
	private static final int VORBIS_MAGIC_AT = 1;
	private static final int VORBIS_RATE_AT = 12;
	private static final int OPUS_HEAD_BYTES = 12;
	private static final int OPUS_HEAD_H_AT = 4;
	private static final int OPUS_PRE_SKIP_AT = 10;
	private static final long OPUS_RATE = 48000;
	private static final byte[] OGG_CAPTURE = {'O', 'g', 'g', 'S'};

	private TrackDuration() {
	}

	/**
	 * Reads a track's duration.
	 *
	 * @param file the audio file
	 * @return the duration in whole milliseconds, or empty when unknown
	 */
	public static OptionalLong of(Path file) {
		try {
			String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
			int dot = name.lastIndexOf('.');
			String ext = dot < 0 ? "" : name.substring(dot + 1);
			long ms = switch (ext) {
				case "wav" -> wav(file);
				case "flac" -> flac(file);
				case "mp3" -> mp3(file);
				case "ogg", "oga" -> ogg(file);
				default -> UNKNOWN;
			};
			return ms >= 0 ? OptionalLong.of(ms) : OptionalLong.empty();
		} catch (IOException | RuntimeException e) {
			return OptionalLong.empty();
		}
	}

	private static ByteBuffer read(RandomAccessFile f, long offset, long max) throws IOException {
		byte[] data = new byte[(int) Math.max(0, Math.min(f.length() - offset, max))];
		f.seek(offset);
		f.readFully(data);
		return ByteBuffer.wrap(data);
	}

	private static long millis(long samples, long rate) {
		return rate <= 0 || samples < 0 ? UNKNOWN : Math.multiplyExact(samples, MILLIS_PER_SECOND) / rate;
	}

	private static long wav(Path file) throws IOException {
		try (RandomAccessFile f = new RandomAccessFile(file.toFile(), "r")) {
			ByteBuffer b = read(f, 0, WINDOW).order(ByteOrder.LITTLE_ENDIAN);
			if (!fourCc(b, 0).equals("RIFF") || !fourCc(b, RIFF_FORM_AT).equals("WAVE")) {
				return UNKNOWN;
			}
			return wavChunks(b, f.length());
		}
	}

	/** Walks the RIFF chunks from the first one: the data size over the byte rate of the preceding fmt chunk. */
	private static long wavChunks(ByteBuffer b, long fileLength) {
		long byteRate = 0;
		long pos = RIFF_FIRST_CHUNK_AT;
		while (pos + CHUNK_HEADER <= b.limit()) {
			int at = (int) pos;
			String id = fourCc(b, at);
			long size = u32(b, at + CHUNK_SIZE_AT);
			if (id.equals("fmt ")) {
				byteRate = u32(b, at + FMT_BYTE_RATE_AT);
			} else if (id.equals("data")) {
				long available = Math.max(0, fileLength - (pos + CHUNK_HEADER));
				return byteRate <= 0 ? UNKNOWN
						: Math.multiplyExact(Math.min(size, available), MILLIS_PER_SECOND) / byteRate;
			}
			pos += CHUNK_HEADER + size + (size & 1);
		}
		return UNKNOWN;
	}

	/** The duration from FLAC STREAMINFO: total samples over sample rate. */
	private static long flac(Path file) throws IOException {
		try (RandomAccessFile f = new RandomAccessFile(file.toFile(), "r")) {
			ByteBuffer b = read(f, 0, WINDOW).order(ByteOrder.BIG_ENDIAN);
			boolean streamInfo = b.limit() >= FLAC_STREAMINFO_END && fourCc(b, 0).equals("fLaC")
					&& (b.get(FLAC_BLOCK_TYPE_AT) & FLAC_BLOCK_TYPE_MASK) == 0;
			if (!streamInfo) {
				return UNKNOWN;
			}
			long packed = b.getLong(FLAC_RATE_SAMPLES_AT);
			long samples = packed & FLAC_SAMPLES_MASK;
			return samples == 0 ? UNKNOWN : millis(samples, packed >>> FLAC_RATE_SHIFT);
		}
	}

	/** The duration of an MP3 after its ID3v2 tag, from a Xing/VBRI header or the first frame's bitrate. */
	private static long mp3(Path file) throws IOException {
		try (RandomAccessFile f = new RandomAccessFile(file.toFile(), "r")) {
			long start = 0;
			ByteBuffer b = read(f, 0, WINDOW);
			if (b.limit() >= ID3_HEADER && fourCc(b, 0).startsWith("ID3")) {
				start = ID3_HEADER + Bytes.syncsafe(b, ID3_SIZE_AT);
				if ((b.get(ID3_FLAGS_AT) & ID3_FOOTER_FLAG) != 0) {
					start += ID3_HEADER;
				}
				b = read(f, start, WINDOW);
			}
			return mp3Frames(b, start, f.length());
		}
	}

	/**
	 * A valid MPEG Layer III frame header.
	 *
	 * @param kbps the bitrate in kilobits per second
	 * @param rate the sample rate in hertz
	 * @param channels 1 for mono, 2 otherwise
	 */
	public record MpegHeader(int kbps, int rate, int channels) {
	}

	/**
	 * Decodes the header of an MPEG-1/2/2.5 Layer III frame.
	 *
	 * @param h the 4 header bytes, big-endian
	 * @return the header, or empty when {@code h} is not a valid Layer III header
	 */
	public static Optional<MpegHeader> mpegHeader(int h) {
		int version = h >>> MPEG_VERSION_SHIFT & TWO_BITS;
		int bitrateIndex = h >>> MPEG_BITRATE_SHIFT & FOUR_BITS;
		int rateIndex = h >>> MPEG_RATE_SHIFT & TWO_BITS;
		if (!isLayer3Frame(h, version) || !isValidIndex(bitrateIndex, rateIndex)) {
			return Optional.empty();
		}
		int rate = MPEG1_RATES[rateIndex] / rateDivisor(version);
		int kbps = (version == VERSION_1 ? MPEG1_L3_KBPS : MPEG2_L3_KBPS)[bitrateIndex];
		int channels = (h >>> MPEG_MODE_SHIFT & TWO_BITS) == MODE_MONO ? 1 : 2;
		return Optional.of(new MpegHeader(kbps, rate, channels));
	}

	private static boolean isLayer3Frame(int h, int version) {
		boolean synced = (h & MPEG_SYNC) == MPEG_SYNC;
		return synced && version != VERSION_RESERVED && (h >>> MPEG_LAYER_SHIFT & TWO_BITS) == LAYER_3;
	}

	private static boolean isValidIndex(int bitrateIndex, int rateIndex) {
		return bitrateIndex != BITRATE_FREE && bitrateIndex != BITRATE_BAD && rateIndex != RATE_RESERVED;
	}

	private static int rateDivisor(int version) {
		if (version == VERSION_1) {
			return 1;
		}
		return version == VERSION_2 ? MPEG2_RATE_DIVISOR : MPEG25_RATE_DIVISOR;
	}

	/**
	 * Finds the first frame in {@code b} and reads the duration from its Xing/Info or VBRI tag, or from the
	 * bitrate when there is none.
	 *
	 * @param b the bytes from {@code base}
	 * @param base the file offset of {@code b}'s first byte
	 * @param fileSize the file length
	 * @return the duration in milliseconds, or -1 when no frame is found
	 */
	private static long mp3Frames(ByteBuffer b, long base, long fileSize) {
		for (int i = 0; i + FOUR_CC <= b.limit(); i++) {
			int h = b.getInt(i);
			Optional<MpegHeader> header = mpegHeader(h);
			if (header.isPresent()) {
				return frameDuration(b, i, h, header.get(), fileSize - (base + i));
			}
		}
		return UNKNOWN;
	}

	private static long frameDuration(ByteBuffer b, int frame, int h, MpegHeader header, long audioBytes) {
		boolean mpeg1 = (h >>> MPEG_VERSION_SHIFT & TWO_BITS) == VERSION_1;
		long samplesPerFrame = mpeg1 ? MPEG1_SAMPLES : MPEG2_SAMPLES;
		long frames = taggedFrames(b, frame + FOUR_CC + sideInfo(mpeg1, header.channels() == 1), frame);
		if (frames >= 0) {
			return millis(frames * samplesPerFrame, header.rate());
		}
		return audioBytes <= 0 ? UNKNOWN : audioBytes * BITS_PER_BYTE / header.kbps();
	}

	private static int sideInfo(boolean mpeg1, boolean mono) {
		if (mpeg1) {
			return mono ? MPEG1_SIDE_MONO : MPEG1_SIDE_STEREO;
		}
		return mono ? MPEG2_SIDE_MONO : MPEG2_SIDE_STEREO;
	}

	/** The frame count of a Xing/Info tag at {@code xingAt} or a VBRI tag after {@code frame}, or -1. */
	private static long taggedFrames(ByteBuffer b, int xingAt, int frame) {
		if (xingAt + XING_TAG_BYTES <= b.limit()) {
			String id = fourCc(b, xingAt);
			boolean xing = id.equals("Xing") || id.equals("Info");
			if (xing && (b.getInt(xingAt + XING_FLAGS_AT) & XING_HAS_FRAMES) != 0) {
				return u32(b, xingAt + XING_FRAMES_AT);
			}
		}
		int vbri = frame + FOUR_CC + VBRI_AT;
		if (vbri + VBRI_TAG_BYTES <= b.limit() && fourCc(b, vbri).equals("VBRI")) {
			return u32(b, vbri + VBRI_FRAMES_AT);
		}
		return UNKNOWN;
	}

	/**
	 * Reads an Ogg Vorbis or Opus duration: the last page's granule position over the codec's sample rate.
	 *
	 * @param file the Ogg file
	 * @return the duration in milliseconds, or -1 when unknown
	 * @throws IOException when the file cannot be read
	 */
	private static long ogg(Path file) throws IOException {
		try (RandomAccessFile f = new RandomAccessFile(file.toFile(), "r")) {
			long length = f.length();
			ByteBuffer first = read(f, 0, WINDOW).order(ByteOrder.LITTLE_ENDIAN);
			if (first.limit() <= OGG_PAGE_HEADER || !fourCc(first, 0).equals("OggS")) {
				return UNKNOWN;
			}
			int packet = OGG_PAGE_HEADER + u8(first, OGG_SEGMENTS_AT);
			long[] rateAndSkip = codec(first, packet);
			if (rateAndSkip == null) {
				return UNKNOWN;
			}
			long granule = lastGranule(read(f, Math.max(0, length - WINDOW), WINDOW).array());
			return granule < 0 ? UNKNOWN : millis(Math.max(0, granule - rateAndSkip[1]), rateAndSkip[0]);
		}
	}

	/** The sample rate and pre-skip of the identification packet at {@code packet}, or null for another codec. */
	private static long[] codec(ByteBuffer first, int packet) {
		boolean vorbis = packet + VORBIS_ID_BYTES <= first.limit() && first.get(packet) == VORBIS_PACKET_TYPE
				&& fourCc(first, packet + VORBIS_MAGIC_AT).equals("vorb");
		if (vorbis) {
			return new long[] {u32(first, packet + VORBIS_RATE_AT), 0};
		}
		boolean opus = packet + OPUS_HEAD_BYTES <= first.limit() && fourCc(first, packet).equals("Opus")
				&& first.get(packet + OPUS_HEAD_H_AT) == 'H';
		return opus ? new long[] {OPUS_RATE, u16(first, packet + OPUS_PRE_SKIP_AT)} : null;
	}

	/** The granule position of the last page whose capture pattern lies in {@code tail}, or -1. */
	private static long lastGranule(byte[] tail) {
		for (int i = tail.length - OGG_PAGE_HEADER; i >= 0; i--) {
			if (Arrays.equals(tail, i, i + FOUR_CC, OGG_CAPTURE, 0, FOUR_CC)) {
				long g = ByteBuffer.wrap(tail, i + OGG_GRANULE_AT, Long.BYTES).order(ByteOrder.LITTLE_ENDIAN).getLong();
				if (g >= 0) {
					return g;
				}
			}
		}
		return UNKNOWN;
	}
}
