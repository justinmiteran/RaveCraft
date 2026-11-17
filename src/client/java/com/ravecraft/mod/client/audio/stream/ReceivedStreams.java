package com.ravecraft.mod.client.audio.stream;

import com.ravecraft.mod.client.audio.SpeakerSoundInstance;
import com.ravecraft.mod.core.audio.stream.StreamPlayout;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedSet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;

/**
 * The streams this client is hearing, relayed or its own (loopback). A stream starts on its first chunk, with no
 * prior notice, so players walking into range just start hearing. Client thread only.
 */
public final class ReceivedStreams {
	private static final long IDLE_TIMEOUT_MS = 5_000;
	private static final int REMEMBERED_ENDED = 64;
	private static final Map<Integer, Stream> STREAMS = new HashMap<>();
	/** Recently ended sessions, so late chunks do not restart them. */
	private static final SequencedSet<Integer> ENDED = new LinkedHashSet<>();

	private ReceivedStreams() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> endAll());
	}

	public static void offer(int sessionId, BlockPos pos, int seq, List<byte[]> frames) {
		Stream stream = STREAMS.get(sessionId);
		if (stream == null) {
			if (ENDED.contains(sessionId)) {
				return;
			}
			stream = new Stream(new StreamPlayout(Util.getMillis()));
			STREAMS.put(sessionId, stream);
			Stream started = stream;
			Minecraft.getInstance().getSoundManager().play(
					new SpeakerSoundInstance(pos, () -> new NetworkAudioStream(started.playout), started::alive));
		}
		stream.playout.offer(seq, frames, Util.getMillis());
	}

	/** Ends a session: after its buffered tail when {@code drain}, at once otherwise. */
	public static void end(int sessionId, boolean drain) {
		remember(sessionId);
		Stream stream = STREAMS.get(sessionId);
		if (stream == null) {
			return;
		}
		if (drain) {
			stream.playout.finish();
		} else {
			stream.stopped = true;
			STREAMS.remove(sessionId);
		}
	}

	public static void endAll() {
		STREAMS.values().forEach(stream -> stream.stopped = true);
		STREAMS.clear();
		ENDED.clear();
	}

	private static void remember(int sessionId) {
		ENDED.add(sessionId);
		while (ENDED.size() > REMEMBERED_ENDED) {
			ENDED.removeFirst();
		}
	}

	/** Drops streams that played out or went silent (owner vanished without notice). */
	private static void tick() {
		if (STREAMS.isEmpty()) {
			return;
		}
		long now = Util.getMillis();
		Iterator<Map.Entry<Integer, Stream>> it = STREAMS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, Stream> entry = it.next();
			Stream stream = entry.getValue();
			if (stream.playout.isDrained() || stream.playout.idleFor(IDLE_TIMEOUT_MS, now)) {
				stream.stopped = true;
				remember(entry.getKey());
				it.remove();
			}
		}
	}

	private static final class Stream {
		final StreamPlayout playout;
		volatile boolean stopped;

		Stream(StreamPlayout playout) {
			this.playout = playout;
		}

		boolean alive() {
			return !stopped && !playout.isDrained();
		}
	}
}
