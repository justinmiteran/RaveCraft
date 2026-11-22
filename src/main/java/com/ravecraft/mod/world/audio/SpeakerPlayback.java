package com.ravecraft.mod.world.audio;

import com.ravecraft.mod.block.PortableSpeakerBlock;
import com.ravecraft.mod.blockentity.PortableSpeakerBlockEntity;
import com.ravecraft.mod.core.audio.stream.SessionTable.Session;
import com.ravecraft.mod.core.queue.PlayHistory;
import com.ravecraft.mod.core.queue.PlaybackClock;
import com.ravecraft.mod.core.queue.QueueEntry;
import com.ravecraft.mod.core.queue.SpeakerQueue;
import com.ravecraft.mod.network.Net;
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
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;

/**
 * Plays the shared queues ({@link SpeakerCommands} applies players' commands): picks the next playable entry (its owner online), opens a
 * session and orders that owner to stream it, and moves on when it ends, fails or its owner leaves. The queue lives
 * in the speaker's block entity; what plays lives here, in memory, so a loaded speaker is always paused.
 * Server thread only.
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
	private static final Map<SpeakerKey, Playing> PLAYING = new HashMap<>();
	/** What each speaker played before, for PREVIOUS. Memory only, like {@link #PLAYING}. */
	private static final Map<SpeakerKey, PlayHistory> HISTORY = new HashMap<>();
	static final Random RNG = new Random();

	/** The track a speaker streams: its session, its entry, its clock and when it started. */
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
		Optional<SpeakerKey> key = keyOf(sessionId);
		if (durationMs < 0 || key.isEmpty()) {
			return;
		}
		boolean owner = StreamSessions.table().byId(sessionId).map(s -> s.owner().equals(player.getUUID())).orElse(false);
		if (!owner) {
			return;
		}
		UUID entryId = PLAYING.get(key.get()).entryId;
		blockEntity(player.level().getServer(), key.get())
				.ifPresent(be -> update(be, queueOf(be).withDuration(entryId, durationMs)));
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
		Optional<SpeakerKey> key = keyOf(sessionId);
		if (key.isEmpty()) {
			StreamSessions.close(sessionId, player, true);
			return;
		}
		if (StreamSessions.close(sessionId, player, true).isEmpty()) {
			return;
		}
		PLAYING.remove(key.get());
		blockEntity(player.level().getServer(), key.get()).ifPresent(be -> advance(be, true));
	}

	/**
	 * The owner cannot find or read the track of its session: drop it and tell them.
	 *
	 * @param player the owner
	 * @param sessionId the session
	 */
	public static void onStreamResult(ServerPlayer player, int sessionId) {
		Optional<SpeakerKey> key = keyOf(sessionId);
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
		List<SpeakerKey> silent = new ArrayList<>();
		PLAYING.forEach((key, playing) -> {
			long timeout = START_TIMEOUT_TICKS + playing.clock.startPositionMs() / SKIPPED_MS_PER_TICK;
			if (!playing.heard && now - playing.startTick > timeout) {
				silent.add(key);
			}
		});
		for (SpeakerKey key : silent) {
			Playing playing = PLAYING.get(key);
			Optional<Session<SpeakerKey>> session = StreamSessions.table().byId(playing.sessionId);
			ServerPlayer owner = session.map(s -> server.getPlayerList().getPlayer(s.owner())).orElse(null);
			blockEntity(server, key).ifPresent(be -> StreamSessions.endAt((ServerLevel) be.getLevel(), be.getBlockPos()));
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
		for (Session<SpeakerKey> session : StreamSessions.endByOwner(server, player)) {
			Playing playing = PLAYING.get(session.key());
			if (playing != null && playing.sessionId == session.id()) {
				PLAYING.remove(session.key());
				blockEntity(server, session.key()).ifPresent(be -> start(be, onlinePlayers(be, player)));
			}
		}
		SpeakerWatchers.markAllDirty();
	}

	/**
	 * The speaker is switched off, broken or unloaded: stop, remembering where the track was. Safe to call on a
	 * speaker that is not playing.
	 *
	 * @param be the speaker
	 */
	public static void pauseAt(PortableSpeakerBlockEntity be) {
		pause(be);
		if (be.isRemoved()) {
			HISTORY.remove(keyOf(be));
		}
	}

	/**
	 * The server is stopping: pause everything before the world is saved.
	 *
	 * @param server the server
	 */
	public static void pauseAll(MinecraftServer server) {
		for (SpeakerKey key : List.copyOf(PLAYING.keySet())) {
			blockEntity(server, key).ifPresentOrElse(SpeakerPlayback::pause,() -> PLAYING.remove(key));
		}
		PLAYING.clear();
		HISTORY.clear();
		StreamSessions.clear();
	}

	// --- Reading -----------------------------------------------------------------------------------------------

	/**
	 * Whether the speaker at {@code pos} streams a track.
	 *
	 * @param level the speaker's level
	 * @param pos where the speaker stands
	 * @return true while it streams
	 */
	public static boolean isPlaying(ServerLevel level, BlockPos pos) {
		return PLAYING.containsKey(new SpeakerKey(level.dimension(), pos));
	}

	/**
	 * The queue with the position estimated now while it plays, as it should be copied into a dropped item.
	 *
	 * @param be the speaker
	 * @return the queue
	 */
	public static SpeakerQueue liveQueue(PortableSpeakerBlockEntity be) {
		SpeakerQueue queue = queueOf(be);
		Playing playing = be.getLevel() instanceof ServerLevel ? PLAYING.get(keyOf(be)) : null;
		if (playing == null || !queue.current().equals(Optional.of(playing.entryId))) {
			return queue;
		}
		return queue.withPosition(playing.clock.positionAt(Util.getMillis()));
	}

	// --- Internals ---------------------------------------------------------------------------------------------

	/**
	 * Plays the current entry if playable, else the next playable one after it. Pauses when nothing is playable or
	 * the speaker is off.
	 */
	private static void start(PortableSpeakerBlockEntity be, Predicate<UUID> online) {
		ServerLevel level = (ServerLevel) be.getLevel();
		BlockPos pos = be.getBlockPos();
		SpeakerQueue queue = queueOf(be);
		Optional<QueueEntry> pick = powered(be) ? queue.nextPlayable(online, RNG) : Optional.empty();
		ServerPlayer owner = pick.map(e -> level.getServer().getPlayerList().getPlayer(e.owner())).orElse(null);
		Optional<Session<SpeakerKey>> session = owner == null
				? Optional.empty()
				: StreamSessions.open(level, pos, owner, pick.get().entryId());
		if (session.isEmpty()) {
			stop(be);
			SpeakerWatchers.markDirty(level, pos);
			return;
		}
		QueueEntry entry = pick.get();
		if (!queue.current().equals(Optional.of(entry.entryId()))) {
			queue = queue.afterAdvance(entry.entryId(), RNG);
		}
		long startMs = queue.positionMs();
		PLAYING.put(keyOf(be), new Playing(session.get().id(), entry.entryId(), new PlaybackClock(startMs, Util.getMillis()),
				level.getServer().getTickCount()));
		update(be, queue);
		Net.send(owner, new StreamOrderPayload(session.get().id(), level.dimension(), pos, true, entry.entryId(), startMs));
		NowPlaying.start(owner, session.get().id(), pos, entry);
	}

	/** Plays the current entry if playable, else the next playable one, with every online owner. */
	static void play(PortableSpeakerBlockEntity be) {
		start(be, onlinePlayers(be, null));
	}

	/** Stops, forgets the history and empties the queue. */
	static void clear(PortableSpeakerBlockEntity be) {
		stop(be);
		HISTORY.remove(keyOf(be));
		update(be, queueOf(be).clear());
	}

	/** Stops playing, storing the estimated position of the current entry. */
	static void pause(PortableSpeakerBlockEntity be) {
		SpeakerQueue live = liveQueue(be);
		if (stop(be)) {
			update(be, live);
		}
	}

	/** Ends the session and forgets that the speaker plays. Returns whether it was playing. */
	static boolean stop(PortableSpeakerBlockEntity be) {
		boolean was = PLAYING.remove(keyOf(be)) != null;
		StreamSessions.endAt((ServerLevel) be.getLevel(), be.getBlockPos());
		return was;
	}

	/**
	 * Moves past the current entry: at its natural end ({@code natural}: repeat-one replays it) or on NEXT. The entry
	 * left goes to the history. Nothing next: pauses on the current entry, from its start. Plays the next entry when
	 * the speaker was playing, or at a natural end (whose session is already closed).
	 */
	static void advance(PortableSpeakerBlockEntity be, boolean natural) {
		SpeakerQueue queue = queueOf(be);
		Optional<QueueEntry> next = queue.next(onlinePlayers(be, null), natural, RNG);
		boolean wasPlaying = stop(be);
		if (next.isEmpty()) {
			update(be, queue.withPosition(0));
			return;
		}
		UUID nextId = next.get().entryId();
		queue.current().filter(current -> !current.equals(nextId)).ifPresent(current -> historyOf(be).push(current));
		update(be, queue.afterAdvance(nextId, RNG));
		if (natural || wasPlaying) {
			play(be);
		}
	}

	/**
	 * Restarts the current entry when it has played for more than 3 s, else goes back to the latest entry of the
	 * history still queued with its owner online (the current one if none). Keeps playing or paused.
	 */
	static void previous(PortableSpeakerBlockEntity be) {
		SpeakerQueue queue = liveQueue(be);
		Optional<UUID> target = queue.current();
		if (queue.positionMs() <= PREVIOUS_RESTART_MS) {
			PlayHistory history = historyOf(be);
			Predicate<UUID> online = onlinePlayers(be, null);
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
		boolean wasPlaying = stop(be);
		// Going back keeps the shuffle round: the target only leaves what is still to play.
		update(be, queueOf(be).withCurrent(target.get(), 0));
		if (wasPlaying) {
			play(be);
		}
	}

	/**
	 * Removes an entry and purges it from the history. Removing the current one cuts it and makes the entry that
	 * would have played next current, played if the speaker was playing or {@code play}.
	 */
	static void removeEntry(PortableSpeakerBlockEntity be, UUID entryId, boolean play) {
		SpeakerQueue queue = queueOf(be);
		PlayHistory history = HISTORY.get(keyOf(be));
		if (history != null) {
			history.remove(entryId);
		}
		if (!queue.current().equals(Optional.of(entryId))) {
			update(be, queue.remove(entryId));
			return;
		}
		Optional<QueueEntry> next = queue.next(onlinePlayers(be, null), false, RNG)
				.filter(e -> !e.entryId().equals(entryId));
		boolean wasPlaying = stop(be);
		if (next.isEmpty()) {
			update(be, queue.remove(entryId));
			return;
		}
		update(be, queue.afterAdvance(next.get().entryId(), RNG).remove(entryId));
		if (wasPlaying || play) {
			play(be);
		}
	}

	/** The current track of {@code key} cannot be read: remove it, tell its owner, play the next. */
	private static void dropUnreadable(MinecraftServer server, SpeakerKey key, ServerPlayer owner) {
		Playing playing = PLAYING.remove(key);
		Optional<PortableSpeakerBlockEntity> found = blockEntity(server, key);
		if (playing == null || found.isEmpty()) {
			return;
		}
		PortableSpeakerBlockEntity be = found.get();
		queueOf(be).entry(playing.entryId).ifPresent(entry -> {
			if (owner != null) {
				Net.send(owner, new TrackRemovedPayload(entry.title()));
			}
		});
		removeEntry(be, playing.entryId, true);
	}

	private static PlayHistory historyOf(PortableSpeakerBlockEntity be) {
		return HISTORY.computeIfAbsent(keyOf(be), key -> new PlayHistory());
	}

	/** Tells the speaker's watchers that it changed. */
	static void markDirty(PortableSpeakerBlockEntity be) {
		if (be.getLevel() instanceof ServerLevel level) {
			SpeakerWatchers.markDirty(level, be.getBlockPos());
		}
	}

	/** Stores {@code queue} in the speaker and tells its watchers. */
	static void update(PortableSpeakerBlockEntity be, SpeakerQueue queue) {
		be.setData(be.data().withQueue(queue));
		markDirty(be);
	}

	private static Predicate<UUID> onlinePlayers(PortableSpeakerBlockEntity be, UUID leaving) {
		MinecraftServer server = be.getLevel().getServer();
		return id -> !id.equals(leaving) && server.getPlayerList().getPlayer(id) != null;
	}

	/** The speaker's queue as stored. */
	static SpeakerQueue queueOf(PortableSpeakerBlockEntity be) {
		return be.data().queue();
	}

	/** Whether the speaker streams a track. */
	static boolean isPlaying(PortableSpeakerBlockEntity be) {
		return PLAYING.containsKey(keyOf(be));
	}

	private static boolean powered(PortableSpeakerBlockEntity be) {
		return be.getBlockState().getValue(PortableSpeakerBlock.POWERED);
	}

	private static SpeakerKey keyOf(PortableSpeakerBlockEntity be) {
		return new SpeakerKey(be.getLevel().dimension(), be.getBlockPos());
	}

	private static Optional<SpeakerKey> keyOf(int sessionId) {
		for (Map.Entry<SpeakerKey, Playing> entry : PLAYING.entrySet()) {
			if (entry.getValue().sessionId == sessionId) {
				return Optional.of(entry.getKey());
			}
		}
		return Optional.empty();
	}

	private static Optional<PortableSpeakerBlockEntity> blockEntity(MinecraftServer server, SpeakerKey key) {
		ServerLevel level = server.getLevel(key.dimension());
		if (level == null || !level.isLoaded(key.pos())
				|| !(level.getBlockEntity(key.pos()) instanceof PortableSpeakerBlockEntity be)) {
			return Optional.empty();
		}
		return Optional.of(be);
	}

}
