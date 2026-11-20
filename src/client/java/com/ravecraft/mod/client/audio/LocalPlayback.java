package com.ravecraft.mod.client.audio;

import com.ravecraft.mod.client.audio.stream.NowPlayingToasts;
import com.ravecraft.mod.client.audio.stream.ReceivedStreams;
import com.ravecraft.mod.client.audio.stream.StreamEmitter;
import com.ravecraft.mod.core.queue.QueueEntry;
import com.ravecraft.mod.core.queue.SpeakerQueue;
import com.ravecraft.mod.network.payload.QueueAddPayload;
import com.ravecraft.mod.network.payload.StreamClosePayload;
import com.ravecraft.mod.network.payload.StreamOrderPayload;
import com.ravecraft.mod.network.payload.StreamResultPayload;
import com.ravecraft.mod.network.payload.StreamStartedPayload;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.BlockPos;

/**
 * This player's side of the shared queues: adds local tracks to a speaker's queue (sending ids and titles, never
 * paths), and streams them when the server orders it. Client thread only.
 */
public final class LocalPlayback {
	/** One {@link QueueAddPayload} every 5 ticks: the server accepts at most 4 per second. */
	private static final int TICKS_PER_BATCH = 5;
	private static final Map<Integer, StreamEmitter> EMITTERS = new HashMap<>();
	private static final Deque<QueueAddPayload> PENDING = new ArrayDeque<>();
	private static int cooldown;

	private LocalPlayback() {
	}

	public static void register() {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> stopAll());
		ClientTickEvents.END_CLIENT_TICK.register(client -> sendPending());
	}

	/** Appends {@code tracks} (at most a full queue) to the queue of the speaker at {@code pos}. */
	public static void add(BlockPos pos, List<Path> tracks) {
		if (tracks.isEmpty() || !ClientPlayNetworking.canSend(QueueAddPayload.TYPE)) {
			return;
		}
		Map<UUID, Path> registered = new LinkedHashMap<>();
		List<QueueAddPayload.NewEntry> batch = new ArrayList<>();
		for (Path track : tracks.subList(0, Math.min(tracks.size(), SpeakerQueue.MAX_ENTRIES))) {
			UUID id = UUID.randomUUID();
			registered.put(id, track);
			batch.add(new QueueAddPayload.NewEntry(id, titleOf(track)));
			if (batch.size() == QueueAddPayload.MAX_ENTRIES) {
				PENDING.add(new QueueAddPayload(pos.immutable(), batch));
				batch = new ArrayList<>();
			}
		}
		if (!batch.isEmpty()) {
			PENDING.add(new QueueAddPayload(pos.immutable(), batch));
		}
		TrackTableStore.register(registered);
		sendPending();
	}

	/** The title shown to everyone: the file name without its extension. */
	static String titleOf(Path track) {
		String name = track.getFileName().toString();
		int dot = name.lastIndexOf('.');
		String title = dot > 0 ? name.substring(0, dot) : name;
		return title.length() > QueueEntry.MAX_TITLE ? title.substring(0, QueueEntry.MAX_TITLE) : title;
	}

	private static void sendPending() {
		if (cooldown > 0) {
			cooldown--;
			return;
		}
		QueueAddPayload next = PENDING.poll();
		if (next != null) {
			ClientPlayNetworking.send(next);
			cooldown = TICKS_PER_BATCH - 1;
		}
	}

	/** Leaving the world: the server already ends everything, so nothing is sent. */
	public static void stopAll() {
		EMITTERS.values().forEach(StreamEmitter::cancel);
		EMITTERS.clear();
		PENDING.clear();
		cooldown = 0;
	}

	/** The server orders this player to stream one of its queued tracks (start), or to stop it. */
	public static void onOrder(StreamOrderPayload order) {
		int id = order.sessionId();
		if (!order.start()) {
			StreamEmitter emitter = EMITTERS.remove(id);
			if (emitter != null) {
				emitter.cancel();
			}
			ReceivedStreams.end(id, false);
			NowPlayingToasts.onSessionEnd(id);
			return;
		}
		Optional<Path> track = TrackTableStore.resolve(order.entryId()).filter(Files::isReadable);
		if (track.isEmpty()) {
			ClientPlayNetworking.send(new StreamResultPayload(id));
			return;
		}
		StreamEmitter previous = EMITTERS.remove(id);
		if (previous != null) {
			previous.cancel();
		}
		StreamEmitter emitter = new StreamEmitter(id, order.dimension(), order.pos(), track.get(), order.positionMs(),
				durationMs -> ClientPlayNetworking.send(new StreamStartedPayload(id, durationMs)), () -> finished(id), e -> failed(id));
		EMITTERS.put(id, emitter);
		emitter.start();
	}

	private static void finished(int id) {
		if (EMITTERS.remove(id) != null) {
			ReceivedStreams.end(id, true);
			ClientPlayNetworking.send(new StreamClosePayload(id, true));
		}
	}

	private static void failed(int id) {
		if (EMITTERS.remove(id) != null) {
			ReceivedStreams.end(id, false);
			ClientPlayNetworking.send(new StreamResultPayload(id));
		}
	}
}
