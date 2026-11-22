package com.ravecraft.mod.client.audio.stream;

import com.ravecraft.mod.client.audio.render.AcousticRenderer;
import com.ravecraft.mod.core.audio.stream.SessionRoster;
import com.ravecraft.mod.core.audio.stream.StreamPlayout;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
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

	/** Ticks the streams each client tick and ends them all on disconnect. */
	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> endAll());
	}

	/** A chunk of session {@code sessionId}, played by speaker {@code speakerId} (placed from its host). */
	public static void offer(int sessionId, UUID speakerId, int seq, List<byte[]> frames) {
		long now = Util.getMillis();
		STREAMS.admit(sessionId, () -> new Stream(speakerId, new StreamPlayout(now))).ifPresent(stream -> {
			stream.playout.offer(seq, frames, now);
			AcousticRenderer.attach(sessionId, stream.speakerId, stream.playout, stream::alive);
		});
	}

	/**
	 * Ends a session: after its buffered tail when {@code drain}, at once otherwise.
	 *
	 * @param sessionId the session to end
	 * @param drain true to play the buffered audio first
	 */
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

	/** Stops every stream, playing or draining, and detaches them from the renderer. */
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

	/** A received session: the speaker that plays it and its playout. */
	private static final class Stream {
		final UUID speakerId;
		final StreamPlayout playout;
		volatile boolean stopped;

		Stream(UUID speakerId, StreamPlayout playout) {
			this.speakerId = speakerId;
			this.playout = playout;
		}

		/** Read by the audio thread: false once stopped or fully played out. */
		boolean alive() {
			return !stopped && !playout.isDrained();
		}
	}
}
