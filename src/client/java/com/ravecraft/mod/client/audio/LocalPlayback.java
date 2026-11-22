package com.ravecraft.mod.client.audio;

import com.ravecraft.mod.client.audio.stream.NowPlayingToasts;
import com.ravecraft.mod.client.audio.stream.ReceivedStreams;
import com.ravecraft.mod.client.audio.stream.StreamEmitter;
import com.ravecraft.mod.client.library.OffThread;
import com.ravecraft.mod.client.library.TrackInfos;
import com.ravecraft.mod.core.audio.tags.TrackInfo;
import com.ravecraft.mod.core.library.AudioFiles;
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
	/** Adds whose tags are read, waiting for the earlier ones so the queue keeps the order of the calls. */
	private static final Map<Long, Runnable> READY = new HashMap<>();
	private static long nextTicket;
	private static long nextToApply;
	/** Bumped on leaving the world, so reads still running then are dropped. */
	private static int addEpoch;
	private static int cooldown;

	private LocalPlayback() {
	}

	/** Sends the queued additions each tick and stops every local stream on leaving. */
	public static void register() {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> stopAll());
		ClientTickEvents.END_CLIENT_TICK.register(client -> sendPending());
	}

	/**
	 * Appends {@code tracks} (at most a full queue) to the queue of the speaker at {@code pos}. The tags are read off
	 * the client thread; calls are enqueued in the order they were made even when their reads finish out of order.
	 *
	 * @param pos where the speaker stands
	 * @param tracks the files to queue, in order
	 */
	public static void add(BlockPos pos, List<Path> tracks) {
		if (tracks.isEmpty() || !ClientPlayNetworking.canSend(QueueAddPayload.TYPE)) {
			return;
		}
		List<Path> capped = List.copyOf(tracks.subList(0, Math.min(tracks.size(), SpeakerQueue.MAX_ENTRIES)));
		BlockPos at = pos.immutable();
		int epoch = addEpoch;
		// One ticket and one read task per chunk: the first chunk reaches the server after about MAX_ENTRIES reads
		// instead of after the whole folder, and the tickets keep the chunks in order.
		for (int from = 0; from < capped.size(); from += QueueAddPayload.MAX_ENTRIES) {
			List<Path> chunk = capped.subList(from, Math.min(capped.size(), from + QueueAddPayload.MAX_ENTRIES));
			List<Optional<TrackInfo>> known = chunk.stream().map(TrackInfos::cached).toList();
			long ticket = nextTicket++;
			OffThread.run(() -> {
				List<String> titles = new ArrayList<>(chunk.size());
				for (int i = 0; i < chunk.size(); i++) {
					Path track = chunk.get(i);
					titles.add(titleOf(track, known.get(i).orElseGet(() -> TrackInfos.readNow(track))));
				}
				return titles;
			}, titles -> finishAdd(epoch, ticket, at, chunk, titles), () -> finishAdd(epoch, ticket, at, chunk, fallbackTitles(chunk, known)));
		}
	}

	/** Runs the adds that are ready, strictly in ticket order. Client thread. */
	private static void finishAdd(int epoch, long ticket, BlockPos pos, List<Path> tracks, List<String> titles) {
		if (epoch != addEpoch) {
			return; // left the world meanwhile
		}
		READY.put(ticket, () -> enqueue(pos, tracks, titles));
		Runnable next;
		while ((next = READY.remove(nextToApply)) != null) {
			nextToApply++;
			next.run();
		}
	}

	/** Registers {@code tracks} under fresh entry ids and asks the server to queue them on the speaker at {@code pos}. */
	private static void enqueue(BlockPos pos, List<Path> tracks, List<String> titles) {
		Map<UUID, Path> registered = new LinkedHashMap<>();
		List<QueueAddPayload.NewEntry> batch = new ArrayList<>();
		for (int i = 0; i < tracks.size(); i++) {
			UUID id = UUID.randomUUID();
			registered.put(id, tracks.get(i));
			batch.add(new QueueAddPayload.NewEntry(id, titles.get(i)));
			if (batch.size() == QueueAddPayload.MAX_ENTRIES) {
				PENDING.add(new QueueAddPayload(pos, batch));
				batch = new ArrayList<>();
			}
		}
		if (!batch.isEmpty()) {
			PENDING.add(new QueueAddPayload(pos, batch));
		}
		TrackTableStore.register(registered);
		sendPending();
	}

	/** The title shown to everyone: "Artist — Title" from the tags, otherwise the file name without its extension. */
	static String titleOf(Path track, TrackInfo info) {
		return info.queueTitle(AudioFiles.baseName(track), QueueEntry.MAX_TITLE);
	}

	/** When reading fails: the cached tags where there are some, the file name otherwise. */
	private static List<String> fallbackTitles(List<Path> tracks, List<Optional<TrackInfo>> known) {
		List<String> titles = new ArrayList<>(tracks.size());
		for (int i = 0; i < tracks.size(); i++) {
			titles.add(titleOf(tracks.get(i), known.get(i).orElseGet(() -> TrackInfo.empty(""))));
		}
		return titles;
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
		READY.clear();
		addEpoch++;
		nextToApply = nextTicket;
		cooldown = 0;
	}

	/**
	 * The server orders this player to stream one of its queued tracks (start), or to stop it.
	 *
	 * @param order the server's order
	 */
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
				new StreamEmitter.Callbacks(durationMs -> ClientPlayNetworking.send(new StreamStartedPayload(id, durationMs)),
						() -> finished(id), e -> failed(id)));
		EMITTERS.put(id, emitter);
		emitter.start();
	}

	private static void finished(int id) {
		if (EMITTERS.remove(id) != null) {
			ReceivedStreams.end(id, true);
			NowPlayingToasts.onSessionEnd(id);
			ClientPlayNetworking.send(new StreamClosePayload(id, true));
		}
	}

	private static void failed(int id) {
		if (EMITTERS.remove(id) != null) {
			ReceivedStreams.end(id, false);
			NowPlayingToasts.onSessionEnd(id);
			ClientPlayNetworking.send(new StreamResultPayload(id));
		}
	}
}
