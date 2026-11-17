package com.ravecraft.mod.core.audio.stream;

/** Fixed parameters of a relayed speaker stream: format, packet sizes, timing and limits. */
public final class StreamFormat {
	/** Samples per second; Opus works natively at 48 kHz. */
	public static final int SAMPLE_RATE = 48_000;
	/** One Opus frame: 20 ms of mono audio. */
	public static final int FRAME_SAMPLES = 960;
	public static final int FRAME_MS = 20;
	/** Frames grouped in one network chunk (100 ms). */
	public static final int FRAMES_PER_CHUNK = 5;
	public static final int CHUNK_SAMPLES = FRAME_SAMPLES * FRAMES_PER_CHUNK;
	public static final int BITRATE = 64_000;
	public static final int COMPLEXITY = 5;
	/** Hard cap of one encoded frame, so a chunk stays under 2 KB. */
	public static final int MAX_FRAME_BYTES = 380;
	/** How far ahead of real time the emitter may send. */
	public static final long LEAD_MS = 2_500;
	/** Audio a receiver buffers before it starts playing (1 s). */
	public static final int JITTER_TARGET_FRAMES = 50;
	/** Frames this far ahead of playout force a resync (5 s). */
	public static final int MAX_AHEAD_FRAMES = 250;
	/** Per-sender rate limit: 200 kb/s, with room for the initial lead burst. */
	public static final long RATE_BYTES_PER_SECOND = 25_000;
	public static final long RATE_BURST_BYTES = 40_000;
	/** Players within this distance of a speaker receive its stream (16 audible + 8 margin). */
	public static final double RELAY_RANGE = 24.0;

	private StreamFormat() {
	}
	/** Rate-limit charge per chunk on top of its audio bytes, so empty chunks are not free. */
	public static final int CHUNK_OVERHEAD_BYTES = 32;
}
