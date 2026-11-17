package com.ravecraft.mod.core.audio.stream;

/**
 * Drops the frames before a start position, so a resumed track begins where it was paused. Every frame lasts
 * {@link StreamFormat#FRAME_MS}; a partial frame is dropped whole, so playback never starts before the position.
 */
public final class FrameSkipper {
	private long remaining;

	public FrameSkipper(long startMs) {
		long frameMs = StreamFormat.FRAME_MS;
		this.remaining = startMs <= 0 ? 0 : (startMs + frameMs - 1) / frameMs;
	}

	/** True while the next frame is still before the start position and must be dropped. */
	public boolean skip() {
		if (remaining == 0) {
			return false;
		}
		remaining--;
		return true;
	}
}
