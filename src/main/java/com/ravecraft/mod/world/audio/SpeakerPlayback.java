package com.ravecraft.mod.world.audio;

import com.ravecraft.mod.core.audio.stream.SessionTable.Session;
import com.ravecraft.mod.core.queue.PlayHistory;
import com.ravecraft.mod.core.queue.PlaybackClock;
import com.ravecraft.mod.core.queue.QueueEntry;
import com.ravecraft.mod.core.queue.SpeakerQueue;
import com.ravecraft.mod.network.Net;
import com.ravecraft.mod.network.payload.SpeakerStatePayload;
import com.ravecraft.mod.network.payload.SpeakerStatePayload.StateEntry;
import com.ravecraft.mod.network.payload.StreamOrderPayload;
import com.ravecraft.mod.network.payload.TrackRemovedPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;

/**
 * Plays the shared queues: applies players' commands, picks the next playable entry (its owner online), opens a
 * session and orders that owner to stream it, and moves on when it ends, fails or its owner leaves. The queue lives
 * with the speaker, read and written through its {@link SpeakerHandle}; what plays lives here, in memory, keyed by
 * the speaker id, so a loaded speaker is always paused. Server thread only.
 */
public final class SpeakerPlayback {
	/**
	 * A session whose owner sent nothing for this many server ticks (10 s) is treated as an unreadable track. Counted
	 * in ticks, not wall time, so a lagging server does not drop a track whose chunks it has not processed yet.
	 */
	public static final int START_TIMEOUT_TICKS = 200;
	/**
	 * Resuming at a position, the owner first decodes and drops everything before it, assumed at least 50 times
	 * faster than real time: the timeout grows by one tick per 2.5 s of position.
	 */
	private static final long SKIPPED_MS_PER_TICK = 2_500;
	/** Live position above which PREVIOUS restarts the current entry instead of going back. */
	private static final long PREVIOUS_RESTART_MS = 3_000;
	private static final Map<UUID, Playing> PLAYING = new HashMap<>();
	/** What each speaker played before, for PREVIOUS. Memory only, like {@link #PLAYING}. */
	private static final Map<UUID, PlayHistory> HISTORY = new HashMap<>();
	static final Random RNG = new Random();

	/** The session a speaker plays, the entry it plays and where it is in it. */
	private static final class Playing {
		final int sessionId;
		final UUID entryId;
		final PlaybackClock clock;
		final int startTick;
		boolean heard;

		Playing(int sessionId, UUID entryId, PlaybackClock clock, int startTick) {
			this.sessionId = sessionId;
			this.entryId = entryId;
			this.clock = clock;
			this.startTick = startTick;
		}
	}

	private SpeakerPlayback() {
	}

	/**
	 * The owner's client opened the track of its session, which lasts {@code durationMs} (negative: unknown). Only
	 * the player streaming the current session of a speaker is heard.
	 *
	 * @param player the owner
	 * @param sessionId the session
	 * @param durationMs the track's length, in milliseconds
	 */
	public static void onStreamStarted(ServerPlayer player, int sessionId, long durationMs) {
		Optional<UUID> key = keyOf(sessionId);
		if (durationMs < 0 || key.isEmpty()) {
			return;
		}
		boolean owner = StreamSessions.table().byId(sessionId).map(s -> s.owner().equals(player.getUUID())).orElse(false);
		if (!owner) {
			return;
		}
		UUID entryId = PLAYING.get(key.get()).entryId;
		SpeakerHosts.handle(player.level().getServer(), key.get())
				.ifPresent(speaker -> update(speaker, queueOf(speaker).withDuration(entryId, durationMs)));
	}

	// --- Session outcomes --------------------------------------------------------------------------------------

	/**
	 * The owner closed its session: at the natural end ({@code finished}), or on a decoding failure.
	 *
	 * @param player the owner
	 * @param sessionId the session
	 * @param finished true at the natural end
	 */
	public static void onStreamClose(ServerPlayer player, int sessionId, boolean finished) {
		if (!finished) {
			onStreamResult(player, sessionId);
			return;
		}
		Optional<UUID> key = keyOf(sessionId);
		if (key.isEmpty()) {
			StreamSessions.close(sessionId, player, true);
			return;
		}
		if (StreamSessions.close(sessionId, player, true).isEmpty()) {
			return;
		}
		PLAYING.remove(key.get());
		SpeakerHosts.handle(player.level().getServer(), key.get()).ifPresent(speaker -> advance(speaker, true));
	}

	/**
	 * The owner cannot find or read the track of its session: drop it and tell them.
	 *
	 * @param player the owner
	 * @param sessionId the session
	 */
	public static void onStreamResult(ServerPlayer player, int sessionId) {
		Optional<UUID> key = keyOf(sessionId);
		if (key.isEmpty() || StreamSessions.close(sessionId, player, false).isEmpty()) {
			return;
		}
		dropUnreadable(player.level().getServer(), key.get(), player);
	}

	/**
	 * An accepted chunk proves the owner is streaming.
	 *
	 * @param sessionId the session
	 */
	public static void onChunk(int sessionId) {
		for (Playing playing : PLAYING.values()) {
			if (playing.sessionId == sessionId) {
				playing.heard = true;
				return;
			}
		}
	}

	/**
	 * Treats sessions whose owner never sent anything as unreadable tracks. Iterates only the playing speakers.
	 *
	 * @param server the server
	 */
	public static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		List<UUID> silent = new ArrayList<>();
		PLAYING.forEach((key, playing) -> {
			long timeout = START_TIMEOUT_TICKS + playing.clock.startPositionMs() / SKIPPED_MS_PER_TICK;
			if (!playing.heard && now - playing.startTick > timeout) {
				silent.add(key);
			}
		});
		for (UUID key : silent) {
			Playing playing = PLAYING.get(key);
			Optional<Session<UUID>> session = StreamSessions.table().byId(playing.sessionId);
			ServerPlayer owner = session.map(s -> server.getPlayerList().getPlayer(s.owner())).orElse(null);
			StreamSessions.endAt(server, key);
			dropUnreadable(server, key, owner);
		}
	}

	// --- Players and lifecycle ---------------------------------------------------------------------------------

	/**
	 * A player joined: their entries are playable again, which watchers should see. Never resumes playback.
	 *
	 * @param player the player who joined
	 */
	public static void onJoin(ServerPlayer player) {
		SpeakerWatchers.markAllDirty();
	}

	/**
	 * A player left: their sessions end and those speakers move on to the next playable entry.
	 *
	 * @param server the server
	 * @param player the player who left
	 */
	public static void onDisconnect(MinecraftServer server, UUID player) {
		for (Session<UUID> session : StreamSessions.endByOwner(server, player)) {
			Playing playing = PLAYING.get(session.key());
			if (playing != null && playing.sessionId == session.id()) {
				PLAYING.remove(session.key());
				SpeakerHosts.handle(server, session.key()).ifPresent(speaker -> start(speaker, onlinePlayers(speaker, player)));
			}
		}
		SpeakerWatchers.markAllDirty();
	}

	/**
	 * Speaker {@code speakerId} is switched off: stop, remembering where the track was. Safe to call on a speaker that
	 * is not playing, or that is not loaded (then only its session ends).
	 */
	public static void pauseAt(MinecraftServer server, UUID speakerId) {
		SpeakerHosts.handle(server, speakerId).ifPresentOrElse(SpeakerPlayback::pause, () -> {
			PLAYING.remove(speakerId);
			StreamSessions.endAt(server, speakerId);
		});
	}

	/**
	 * The speaker leaves the world with its holder (its carrier disconnects): stop, writing where the track was
	 * where it lives, and forget what it played before. Takes a handle built directly: the registry may no longer
	 * resolve a host that is leaving.
	 */
	public static void pauseRemoved(SpeakerHandle speaker) {
		pause(speaker);
		HISTORY.remove(speaker.id());
	}

	/** Speaker {@code speakerId} is gone for good (destroyed, despawned): end its session and forget its history. */
	public static void stopAt(MinecraftServer server, UUID speakerId) {
		PLAYING.remove(speakerId);
		HISTORY.remove(speakerId);
		StreamSessions.endAt(server, speakerId);
	}

	/**
	 * The host of speaker {@code speakerId} is out of reach (a chest, an unloaded chunk): end its session and return
	 * {@code lastStored}, its last known queue, at the position the track is at now, for when it shows up again.
	 */
	public static SpeakerQueue detach(MinecraftServer server, UUID speakerId, SpeakerQueue lastStored) {
		SpeakerQueue live = liveQueue(speakerId, lastStored);
		PLAYING.remove(speakerId);
		StreamSessions.endAt(server, speakerId);
		return live;
	}

	/** A speaker paused by losing its host is back: play from the stored position, when powered. */
	public static void resume(SpeakerHandle speaker) {
		if (speaker.powered() && !isPlaying(speaker.id())) {
			start(speaker, onlinePlayers(speaker, null));
		}
	}

	/**
	 * The server is stopping: pause everything before the world is saved.
	 *
	 * @param server the server
	 */
	public static void pauseAll(MinecraftServer server) {
		for (UUID key : List.copyOf(PLAYING.keySet())) {
			SpeakerHosts.handle(server, key).ifPresentOrElse(SpeakerPlayback::pause, () -> PLAYING.remove(key));
		}
		PLAYING.clear();
		HISTORY.clear();
		StreamSessions.clear();
	}

	// --- Reading -----------------------------------------------------------------------------------------------

	/** Whether speaker {@code speakerId} plays a session now. */
	public static boolean isPlaying(UUID speakerId) {
		return PLAYING.containsKey(speakerId);
	}

	/**
	 * The {@code stored} queue of speaker {@code speakerId}, with the position estimated now while it plays: what
	 * should be copied into a dropped item or a save.
	 */
	public static SpeakerQueue liveQueue(UUID speakerId, SpeakerQueue stored) {
		Playing playing = PLAYING.get(speakerId);
		if (playing == null || !stored.current().equals(Optional.of(playing.entryId))) {
			return stored;
		}
		return stored.withPosition(playing.clock.positionAt(Util.getMillis()));
	}

	/** What watchers of {@code speaker} see. */
	public static SpeakerStatePayload stateOf(SpeakerHandle speaker) {
		SpeakerQueue queue = liveQueue(speaker);
		MinecraftServer server = speaker.level().getServer();
		List<StateEntry> entries = new ArrayList<>();
		for (QueueEntry entry : queue.displayOrder()) {
			boolean online = server.getPlayerList().getPlayer(entry.owner()) != null;
			entries.add(new StateEntry(entry.entryId(), entry.ownerName(), entry.title(), online, entry.durationMs()));
		}
		return new SpeakerStatePayload(speaker.id(), isPlaying(speaker.id()), queue.current(), queue.positionMs(), entries,
				queue.repeat(), queue.shuffle(), speaker.data().settings().volume(), speaker.powered(),
				queue.nextRoundIndex());
	}

	// --- Internals ---------------------------------------------------------------------------------------------

	/**
	 * Plays the current entry if playable, else the next playable one after it. Pauses when nothing is playable or
	 * the speaker is off.
	 */
	private static void start(SpeakerHandle speaker, Predicate<UUID> online) {
		MinecraftServer server = speaker.level().getServer();
		SpeakerQueue queue = queueOf(speaker);
		Optional<QueueEntry> pick = speaker.powered() ? queue.nextPlayable(online, RNG) : Optional.empty();
		ServerPlayer owner = pick.map(e -> server.getPlayerList().getPlayer(e.owner())).orElse(null);
		Optional<Session<UUID>> session = owner == null
				? Optional.empty()
				: StreamSessions.open(speaker, owner, pick.get().entryId());
		if (session.isEmpty()) {
			stop(speaker);
			markDirty(speaker);
			return;
		}
		QueueEntry entry = pick.get();
		if (!queue.current().equals(Optional.of(entry.entryId()))) {
			queue = queue.afterAdvance(entry.entryId(), RNG);
		}
		long startMs = queue.positionMs();
		PLAYING.put(speaker.id(), new Playing(session.get().id(), entry.entryId(), new PlaybackClock(startMs, Util.getMillis()),
				server.getTickCount()));
		update(speaker, queue);
		// Hosts first: the owner must know where its own stream plays from before its first chunk.
		SpeakerHostNotices.notice(server, speaker.id());
		Net.send(owner, new StreamOrderPayload(session.get().id(), speaker.id(), true, entry.entryId(), startMs));
		NowPlaying.start(owner, session.get().id(), speaker.id(), entry);
	}

	/** Starts the current entry, or the next playable one. */
	static void play(SpeakerHandle speaker) {
		start(speaker, onlinePlayers(speaker, null));
	}

	/** Stops, forgets the history and empties the queue. */
	static void clear(SpeakerHandle speaker) {
		stop(speaker);
		HISTORY.remove(speaker.id());
		update(speaker, queueOf(speaker).clear());
	}

	/** Stops playing, storing the estimated position of the current entry. */
	static void pause(SpeakerHandle speaker) {
		SpeakerQueue live = liveQueue(speaker);
		if (stop(speaker)) {
			update(speaker, live);
		}
	}

	/** Ends the session and forgets that the speaker plays. Returns whether it was playing. */
	static boolean stop(SpeakerHandle speaker) {
		boolean was = PLAYING.remove(speaker.id()) != null;
		StreamSessions.endAt(speaker.level().getServer(), speaker.id());
		return was;
	}

	/**
	 * Moves past the current entry: at its natural end ({@code natural}: repeat-one replays it) or on NEXT. The entry
	 * left goes to the history. Nothing next: pauses on the current entry, from its start. Plays the next entry when
	 * the speaker was playing, or at a natural end (whose session is already closed).
	 */
	static void advance(SpeakerHandle speaker, boolean natural) {
		SpeakerQueue queue = queueOf(speaker);
		Optional<QueueEntry> next = queue.next(onlinePlayers(speaker, null), natural, RNG);
		boolean wasPlaying = stop(speaker);
		if (next.isEmpty()) {
			update(speaker, queue.withPosition(0));
			return;
		}
		UUID nextId = next.get().entryId();
		queue.current().filter(current -> !current.equals(nextId)).ifPresent(current -> historyOf(speaker).push(current));
		update(speaker, queue.afterAdvance(nextId, RNG));
		if (natural || wasPlaying) {
			start(speaker, onlinePlayers(speaker, null));
		}
	}

	/**
	 * Restarts the current entry when it has played for more than 3 s, else goes back to the latest entry of the
	 * history still queued with its owner online (the current one if none). Keeps playing or paused.
	 */
	static void previous(SpeakerHandle speaker) {
		SpeakerQueue queue = liveQueue(speaker);
		Optional<UUID> target = queue.current();
		if (queue.positionMs() <= PREVIOUS_RESTART_MS) {
			PlayHistory history = historyOf(speaker);
			Predicate<UUID> online = onlinePlayers(speaker, null);
			for (Optional<UUID> id = history.pop(); id.isPresent(); id = history.pop()) {
				if (queue.entry(id.get()).filter(e -> online.test(e.owner())).isPresent()) {
					target = id;
					break;
				}
			}
		}
		if (target.isEmpty()) {
			return;
		}
		boolean wasPlaying = stop(speaker);
		// Going back keeps the shuffle round: the target only leaves what is still to play.
		update(speaker, queueOf(speaker).withCurrent(target.get(), 0));
		if (wasPlaying) {
			start(speaker, onlinePlayers(speaker, null));
		}
	}

	/**
	 * Removes an entry and purges it from the history. Removing the current one cuts it and makes the entry that
	 * would have played next current, played if the speaker was playing or {@code play}.
	 */
	static void removeEntry(SpeakerHandle speaker, UUID entryId, boolean play) {
		SpeakerQueue queue = queueOf(speaker);
		PlayHistory history = HISTORY.get(speaker.id());
		if (history != null) {
			history.remove(entryId);
		}
		if (!queue.current().equals(Optional.of(entryId))) {
			update(speaker, queue.remove(entryId));
			return;
		}
		Optional<QueueEntry> next = queue.next(onlinePlayers(speaker, null), false, RNG)
				.filter(e -> !e.entryId().equals(entryId));
		boolean wasPlaying = stop(speaker);
		if (next.isEmpty()) {
			update(speaker, queue.remove(entryId));
			return;
		}
		update(speaker, queue.afterAdvance(next.get().entryId(), RNG).remove(entryId));
		if (wasPlaying || play) {
			start(speaker, onlinePlayers(speaker, null));
		}
	}

	/** The current track of speaker {@code key} cannot be read: remove it, tell its owner, play the next. */
	private static void dropUnreadable(MinecraftServer server, UUID key, ServerPlayer owner) {
		Playing playing = PLAYING.remove(key);
		Optional<SpeakerHandle> found = SpeakerHosts.handle(server, key);
		if (playing == null || found.isEmpty()) {
			return;
		}
		SpeakerHandle speaker = found.get();
		queueOf(speaker).entry(playing.entryId).ifPresent(entry -> {
			if (owner != null) {
				Net.send(owner, new TrackRemovedPayload(entry.title()));
			}
		});
		removeEntry(speaker, playing.entryId, true);
	}

	private static PlayHistory historyOf(SpeakerHandle speaker) {
		return HISTORY.computeIfAbsent(speaker.id(), key -> new PlayHistory());
	}

	static void markDirty(SpeakerHandle speaker) {
		SpeakerWatchers.markDirty(speaker.id());
	}

	/** Writes the queue where the speaker lives; the handle marks its open screens dirty. */
	static void update(SpeakerHandle speaker, SpeakerQueue queue) {
		speaker.setData(speaker.data().withQueue(queue));
	}

	private static Predicate<UUID> onlinePlayers(SpeakerHandle speaker, UUID leaving) {
		MinecraftServer server = speaker.level().getServer();
		return id -> !id.equals(leaving) && server.getPlayerList().getPlayer(id) != null;
	}

	static SpeakerQueue queueOf(SpeakerHandle speaker) {
		return speaker.data().queue();
	}

	private static SpeakerQueue liveQueue(SpeakerHandle speaker) {
		return liveQueue(speaker.id(), queueOf(speaker));
	}

	private static Optional<UUID> keyOf(int sessionId) {
		for (Map.Entry<UUID, Playing> entry : PLAYING.entrySet()) {
			if (entry.getValue().sessionId == sessionId) {
				return Optional.of(entry.getKey());
			}
		}
		return Optional.empty();
	}

}
