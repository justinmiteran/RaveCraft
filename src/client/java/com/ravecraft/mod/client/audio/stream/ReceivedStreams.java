package com.ravecraft.mod.client.audio.stream;

import com.ravecraft.mod.client.audio.render.AcousticRenderer;
import com.ravecraft.mod.core.audio.stream.SessionRoster;
import com.ravecraft.mod.core.audio.stream.StreamPlayout;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;

/**
 * The streams this client is hearing, relayed or its own (loopback). A stream starts on its first chunk, with no
 * prior notice, so players walking into range just start hearing; one that went quiet is dropped and starts again
 * if chunks come back. Client thread only.
 */
public final class ReceivedStreams {
	private static final long IDLE_TIMEOUT_MS = 5_000;
	private static final int REMEMBERED_ENDED = 64;
	private static final SessionRoster<Stream> STREAMS = new SessionRoster<>(REMEMBERED_ENDED);
	/** Ended streams still playing their buffered tail. */
	private static final List<Stream> DRAINING = new ArrayList<>();

	private ReceivedStreams() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> endAll());
	}

	public static void offer(int sessionId, BlockPos pos, int seq, List<byte[]> frames) {
		long now = Util.getMillis();
		STREAMS.admit(sessionId, () -> new Stream(pos.immutable(), new StreamPlayout(now))).ifPresent(stream -> {
			stream.playout.offer(seq, frames, now);
			AcousticRenderer.attach(sessionId, stream.pos, stream.playout, stream::alive);
		});
	}

	/** Ends a session: after its buffered tail when {@code drain}, at once otherwise. */
	public static void end(int sessionId, boolean drain) {
		STREAMS.end(sessionId).ifPresent(stream -> {
			if (drain) {
				stream.playout.finish();
				DRAINING.add(stream);
			} else {
				stream.stopped = true;
			}
		});
	}

	public static void endAll() {
		STREAMS.clear().forEach(stream -> stream.stopped = true);
		DRAINING.forEach(stream -> stream.stopped = true);
		DRAINING.clear();
		AcousticRenderer.detachAll();
	}

	/** Drops streams that went silent (owner vanished without notice, or this player walked away). */
	private static void tick() {
		long now = Util.getMillis();
		STREAMS.expireIf(stream -> stream.playout.idleFor(IDLE_TIMEOUT_MS, now)).forEach(stream -> stream.stopped = true);
		DRAINING.removeIf(stream -> !stream.alive());
	}

	private static final class Stream {
		final BlockPos pos;
		final StreamPlayout playout;
		volatile boolean stopped;

		Stream(BlockPos pos, StreamPlayout playout) {
			this.pos = pos;
			this.playout = playout;
		}

		/** Read by the audio thread: false once stopped or fully played out. */
		boolean alive() {
			return !stopped && !playout.isDrained();
		}
	}
}
