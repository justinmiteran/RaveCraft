package com.ravecraft.mod.core.acoustics;

/**
 * Real-time clock of a stream that has no OpenAL source: tells how many blocks to consume (and drop) so the stream
 * stays at the right position, ready to be heard again. After a stall it catches up at most
 * {@link #MAX_CATCH_UP} blocks and forgets the rest.
 */
public final class VirtualPacer {
	public static final int MAX_CATCH_UP = 10;

	private final long blockMs;
	private long clockMs;

	/**
	 * Creates a pacer whose clock starts at {@code startMs}.
	 *
	 * @param startMs the current time
	 * @param blockMs the length of one block
	 */
	public VirtualPacer(long startMs, long blockMs) {
		this.blockMs = blockMs;
		this.clockMs = startMs;
	}

	/**
	 * Whole blocks elapsed since the last call; the remainder carries over.
	 *
	 * @param nowMs the current time, in milliseconds
	 * @return the number of whole blocks due
	 */
	public int due(long nowMs) {
		long blocks = Math.max(0, (nowMs - clockMs) / blockMs);
		if (blocks > MAX_CATCH_UP) {
			clockMs = nowMs;
			return MAX_CATCH_UP;
		}
		clockMs += blocks * blockMs;
		return (int) blocks;
	}

	/**
	 * Restarts the clock at {@code nowMs}, dropping the remainder.
	 *
	 * @param nowMs the current time, in milliseconds
	 */
	public void reset(long nowMs) {
		clockMs = nowMs;
	}

	/**
	 * Restarts the clock once {@code pendingBlocks} already taken from the stream (queued but never heard) would have
	 * played, so dropping them does not move the stream ahead of real time.
	 *
	 * @param nowMs the current time, in milliseconds
	 * @param pendingBlocks the blocks taken but never heard
	 */
	public void resetAfter(long nowMs, int pendingBlocks) {
		clockMs = nowMs + pendingBlocks * blockMs;
	}
}
