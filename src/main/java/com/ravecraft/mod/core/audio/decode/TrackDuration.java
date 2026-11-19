package com.ravecraft.mod.core.audio.decode;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * Reads a track's length from its file header, without decoding any audio. Reads at most the first 64 KiB (and,
 * for Ogg, the last 64 KiB). Never throws: anything unreadable or malformed is unknown.
 */
public final class TrackDuration {
	private static final int WINDOW = 64 * 1024;
	private static final int[] MPEG1_L3_KBPS = {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320};
	private static final int[] MPEG2_L3_KBPS = {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160};
	private static final int[] MPEG1_RATES = {44100, 48000, 32000};

	private TrackDuration() {
	}

	/** The duration in whole milliseconds, or empty when unknown. */
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
				default -> -1;
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

	private static String tag(ByteBuffer b, int at) {
		byte[] t = new byte[4];
		b.get(at, t);
		return new String(t, StandardCharsets.ISO_8859_1);
	}

	private static long millis(long samples, long rate) {
		return rate <= 0 || samples < 0 ? -1 : Math.multiplyExact(samples, 1000L) / rate;
	}

	private static long wav(Path file) throws IOException {
		try (RandomAccessFile f = new RandomAccessFile(file.toFile(), "r")) {
			ByteBuffer b = read(f, 0, WINDOW).order(ByteOrder.LITTLE_ENDIAN);
			if (!tag(b, 0).equals("RIFF") || !tag(b, 8).equals("WAVE")) {
				return -1;
			}
			long byteRate = 0;
			long pos = 12;
			while (pos + 8 <= b.limit()) {
				int at = (int) pos;
				String id = tag(b, at);
				long size = b.getInt(at + 4) & 0xFFFFFFFFL;
				if (id.equals("fmt ")) {
					byteRate = b.getInt(at + 16) & 0xFFFFFFFFL;
				} else if (id.equals("data")) {
					if (byteRate <= 0) {
						return -1;
					}
					long available = Math.max(0, f.length() - (pos + 8));
					return Math.multiplyExact(Math.min(size, available), 1000L) / byteRate;
				}
				pos += 8 + size + (size & 1);
			}
			return -1;
		}
	}

	private static long flac(Path file) throws IOException {
		try (RandomAccessFile f = new RandomAccessFile(file.toFile(), "r")) {
			ByteBuffer b = read(f, 0, WINDOW).order(ByteOrder.BIG_ENDIAN);
			if (b.limit() < 42 || !tag(b, 0).equals("fLaC") || (b.get(4) & 0x7F) != 0) {
				return -1;
			}
			long packed = b.getLong(18);
			long rate = packed >>> 44;
			long samples = packed & 0xFFFFFFFFFL;
			return samples == 0 ? -1 : millis(samples, rate);
		}
	}

	private static long mp3(Path file) throws IOException {
		try (RandomAccessFile f = new RandomAccessFile(file.toFile(), "r")) {
			long start = 0;
			ByteBuffer b = read(f, 0, WINDOW);
			if (b.limit() >= 10 && tag(b, 0).startsWith("ID3")) {
				start = 10 + ((b.get(6) & 0x7F) << 21 | (b.get(7) & 0x7F) << 14 | (b.get(8) & 0x7F) << 7
						| (b.get(9) & 0x7F));
				if ((b.get(5) & 0x10) != 0) {
					start += 10;
				}
				b = read(f, start, WINDOW);
			}
			return mp3Frames(b, start, f.length());
		}
	}

	/** {@code base} is the file offset of {@code b}'s first byte. */
	private static long mp3Frames(ByteBuffer b, long base, long fileSize) {
		for (int i = 0; i + 4 <= b.limit(); i++) {
			int h = b.getInt(i);
			if ((h & 0xFFE00000) != 0xFFE00000) {
				continue;
			}
			int version = (h >>> 19) & 3; // 3 = MPEG-1, 2 = MPEG-2, 0 = MPEG-2.5
			int layer = (h >>> 17) & 3; // 1 = Layer III
			int bitrateIndex = (h >>> 12) & 15;
			int rateIndex = (h >>> 10) & 3;
			if (version == 1 || layer != 1 || bitrateIndex == 0 || bitrateIndex == 15 || rateIndex == 3) {
				continue;
			}
			boolean mpeg1 = version == 3;
			int rate = MPEG1_RATES[rateIndex] / (mpeg1 ? 1 : version == 2 ? 2 : 4);
			int kbps = (mpeg1 ? MPEG1_L3_KBPS : MPEG2_L3_KBPS)[bitrateIndex];
			int samplesPerFrame = mpeg1 ? 1152 : 576;
			boolean mono = ((h >>> 6) & 3) == 3;
			int side = mpeg1 ? (mono ? 17 : 32) : (mono ? 9 : 17);
			int tagAt = i + 4 + side;
			if (tagAt + 12 <= b.limit()) {
				String id = tag(b, tagAt);
				if ((id.equals("Xing") || id.equals("Info")) && (b.getInt(tagAt + 4) & 1) != 0) {
					long frames = b.getInt(tagAt + 8) & 0xFFFFFFFFL;
					return millis(frames * samplesPerFrame, rate);
				}
			}
			int vbri = i + 4 + 32;
			if (vbri + 18 <= b.limit() && tag(b, vbri).equals("VBRI")) {
				long frames = b.getInt(vbri + 14) & 0xFFFFFFFFL;
				return millis(frames * samplesPerFrame, rate);
			}
			long audioBytes = fileSize - (base + i);
			return audioBytes <= 0 ? -1 : audioBytes * 8 / kbps;
		}
		return -1;
	}

	private static long ogg(Path file) throws IOException {
		try (RandomAccessFile f = new RandomAccessFile(file.toFile(), "r")) {
			long length = f.length();
			ByteBuffer first = read(f, 0, WINDOW).order(ByteOrder.LITTLE_ENDIAN);
			if (first.limit() < 28 || !tag(first, 0).equals("OggS")) {
				return -1;
			}
			int packet = 27 + (first.get(26) & 0xFF);
			long rate;
			long preSkip = 0;
			if (packet + 16 <= first.limit() && first.get(packet) == 1
					&& tag(first, packet + 1).equals("vorb")) {
				rate = first.getInt(packet + 12) & 0xFFFFFFFFL;
			} else if (packet + 12 <= first.limit() && tag(first, packet).equals("Opus")
					&& first.get(packet + 4) == 'H') {
				rate = 48000;
				preSkip = first.getShort(packet + 10) & 0xFFFF;
			} else {
				return -1;
			}
			byte[] tail = read(f, Math.max(0, length - WINDOW), WINDOW).array();
			long granule = -1;
			for (int i = tail.length - 27; i >= 0; i--) {
				if (tail[i] == 'O' && tail[i + 1] == 'g' && tail[i + 2] == 'g' && tail[i + 3] == 'S') {
					long g = ByteBuffer.wrap(tail, i + 6, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
					if (g >= 0) {
						granule = g;
						break;
					}
				}
			}
			return granule < 0 ? -1 : millis(Math.max(0, granule - preSkip), rate);
		}
	}
}
