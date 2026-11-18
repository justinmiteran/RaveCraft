package com.ravecraft.mod.core.acoustics.debug;

import org.jspecify.annotations.Nullable;

/** One received stream: its buffer, lost frames, stalls, bit rate, and the track and who queued it when known. */
public record StreamRow(int session, int bufferedMs, long lostFrames, long underruns, double kbps,
		@Nullable String title, @Nullable String owner) {
}
