package com.ravecraft.mod.core.audio.stream;

/**
 * How a received stream is doing: the audio waiting in its jitter buffer, the times it ran empty while playing, the
 * frames that never came, and the Opus bytes received since it started.
 */
public record PlayoutStats(int bufferedMs, long underruns, long lostFrames, long receivedBytes) {
}
