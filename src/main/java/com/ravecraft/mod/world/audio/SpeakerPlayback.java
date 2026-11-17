package com.ravecraft.mod.world.audio;

import com.ravecraft.mod.block.PortableSpeakerBlock;
import com.ravecraft.mod.blockentity.PortableSpeakerBlockEntity;
import com.ravecraft.mod.core.audio.stream.SessionTable.Session;
import com.ravecraft.mod.core.queue.PlaybackClock;
import com.ravecraft.mod.core.queue.QueueEntry;
import com.ravecraft.mod.core.queue.SpeakerQueue;
import com.ravecraft.mod.network.Net;
import com.ravecraft.mod.network.payload.QueueAddPayload.NewEntry;
import com.ravecraft.mod.network.payload.SpeakerCommandPayload.Command;
import com.ravecraft.mod.network.payload.SpeakerStatePayload;
import com.ravecraft.mod.network.payload.SpeakerStatePayload.StateEntry;
import com.ravecraft.mod.network.payload.StreamOrderPayload;
import com.ravecraft.mod.network.payload.TrackRemovedPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;

/**
 * Plays the shared queues: applies players' commands, picks the next playable entry (its owner online), opens a
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
	private static final Map<SpeakerKey, Playing> PLAYING = new HashMap<>();

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

	// --- Commands ----------------------------------------------------------------------------------------------

	/** Appends tracks offered by {@code player}, who owns them. Ignored out of reach. */
	public static void add(ServerPlayer player, BlockPos pos, List<NewEntry> added) {
		PortableSpeakerBlockEntity be = speakerInReach(player, pos);
		if (be == null) {
			return;
		}
		String name = player.getName().getString();
		List<QueueEntry> entries = new ArrayList<>();
		for (NewEntry entry : added) {
			entries.add(new QueueEntry(entry.entryId(), player.getUUID(), name, entry.title()));
		}
		update(be, queueOf(be).add(entries));
	}

	public static void command(ServerPlayer player, BlockPos pos, Command command, Optional<UUID> entryId) {
		PortableSpeakerBlockEntity be = speakerInReach(player, pos);
		if (be == null) {
			return;
		}
		switch (command) {
			case PLAY -> {
				if (!isPlaying(be)) {
					start(be, onlinePlayers(be, null));
				}
			}
			case PAUSE -> pause(be);
			case NEXT -> queueOf(be).current().ifPresent(current -> removeEntry(be, current));
			case REMOVE -> entryId.filter(id -> queueOf(be).entry(id).isPresent()).ifPresent(id -> removeEntry(be, id));
			case CLEAR -> {
				stop(be);
				update(be, SpeakerQueue.EMPTY);
			}
		}
	}

	// --- Session outcomes --------------------------------------------------------------------------------------

	/** The owner closed its session: at the natural end ({@code finished}), or on a decoding failure. */
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
		Playing playing = PLAYING.remove(key.get());
		blockEntity(player.level().getServer(), key.get()).ifPresent(be -> {
			update(be, queueOf(be).remove(playing.entryId));
			start(be, onlinePlayers(be, null));
		});
	}

	/** The owner cannot find or read the track of its session: drop it and tell them. */
	public static void onStreamResult(ServerPlayer player, int sessionId) {
		Optional<SpeakerKey> key = keyOf(sessionId);
		if (key.isEmpty() || StreamSessions.close(sessionId, player, false).isEmpty()) {
			return;
		}
		dropUnreadable(player.level().getServer(), key.get(), player);
	}

	/** An accepted chunk proves the owner is streaming. */
	public static void onChunk(int sessionId) {
		for (Playing playing : PLAYING.values()) {
			if (playing.sessionId == sessionId) {
				playing.heard = true;
				return;
			}
		}
	}

	/** Treats sessions whose owner never sent anything as unreadable tracks. Iterates only the playing speakers. */
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

	/** A player joined: their entries are playable again, which watchers should see. Never resumes playback. */
	public static void onJoin(ServerPlayer player) {
		SpeakerWatchers.markAllDirty();
	}

	/** A player left: their sessions end and those speakers move on to the next playable entry. */
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
	 */
	public static void pauseAt(PortableSpeakerBlockEntity be) {
		pause(be);
	}

	/** The server is stopping: pause everything before the world is saved. */
	public static void pauseAll(MinecraftServer server) {
		for (SpeakerKey key : List.copyOf(PLAYING.keySet())) {
			blockEntity(server, key).ifPresentOrElse(SpeakerPlayback::pause,() -> PLAYING.remove(key));
		}
		PLAYING.clear();
		StreamSessions.clear();
	}

	// --- Reading -----------------------------------------------------------------------------------------------

	public static boolean isPlaying(ServerLevel level, BlockPos pos) {
		return PLAYING.containsKey(new SpeakerKey(level.dimension(), pos));
	}

	/** The queue with the position estimated now while it plays, as it should be copied into a dropped item. */
	public static SpeakerQueue liveQueue(PortableSpeakerBlockEntity be) {
		SpeakerQueue queue = queueOf(be);
		Playing playing = be.getLevel() instanceof ServerLevel ? PLAYING.get(keyOf(be)) : null;
		if (playing == null || !queue.current().equals(Optional.of(playing.entryId))) {
			return queue;
		}
		return queue.withPosition(playing.clock.positionAt(Util.getMillis()));
	}

	/** What watchers of the speaker at {@code pos} see. */
	public static SpeakerStatePayload stateOf(ServerLevel level, BlockPos pos) {
		if (!(level.getBlockEntity(pos) instanceof PortableSpeakerBlockEntity be)) {
			return new SpeakerStatePayload(pos, false, Optional.empty(), 0, List.of());
		}
		SpeakerQueue queue = liveQueue(be);
		MinecraftServer server = level.getServer();
		List<StateEntry> entries = new ArrayList<>();
		for (QueueEntry entry : queue.entries()) {
			boolean online = server.getPlayerList().getPlayer(entry.owner()) != null;
			entries.add(new StateEntry(entry.entryId(), entry.ownerName(), entry.title(), online));
		}
		return new SpeakerStatePayload(pos, isPlaying(be), queue.current(), queue.positionMs(), entries);
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
		Optional<QueueEntry> pick = powered(be) ? queue.nextPlayable(online) : Optional.empty();
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
		long startMs = queue.current().equals(Optional.of(entry.entryId())) ? queue.positionMs() : 0;
		PLAYING.put(keyOf(be), new Playing(session.get().id(), entry.entryId(), new PlaybackClock(startMs, Util.getMillis()),
				level.getServer().getTickCount()));
		update(be, queue.withCurrent(entry.entryId(), startMs));
		Net.send(owner, new StreamOrderPayload(session.get().id(), pos, true, entry.entryId(), startMs));
	}

	/** Stops playing, storing the estimated position of the current entry. */
	private static void pause(PortableSpeakerBlockEntity be) {
		SpeakerQueue live = liveQueue(be);
		if (stop(be)) {
			update(be, live);
		}
	}

	/** Ends the session and forgets that the speaker plays. Returns whether it was playing. */
	private static boolean stop(PortableSpeakerBlockEntity be) {
		boolean was = PLAYING.remove(keyOf(be)) != null;
		StreamSessions.endAt((ServerLevel) be.getLevel(), be.getBlockPos());
		return was;
	}

	/** Removes an entry; removing the current one cuts it and, if it was playing, plays the next. */
	private static void removeEntry(PortableSpeakerBlockEntity be, UUID entryId) {
		SpeakerQueue queue = queueOf(be);
		if (!queue.current().equals(Optional.of(entryId))) {
			update(be, queue.remove(entryId));
			return;
		}
		boolean wasPlaying = stop(be);
		update(be, queue.remove(entryId));
		if (wasPlaying) {
			start(be, onlinePlayers(be, null));
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
		SpeakerQueue queue = queueOf(be);
		queue.entry(playing.entryId).ifPresent(entry -> {
			if (owner != null) {
				Net.send(owner, new TrackRemovedPayload(entry.title()));
			}
		});
		update(be, queue.remove(playing.entryId));
		start(be, onlinePlayers(be, null));
	}

	private static void update(PortableSpeakerBlockEntity be, SpeakerQueue queue) {
		be.setData(be.data().withQueue(queue));
		if (be.getLevel() instanceof ServerLevel level) {
			SpeakerWatchers.markDirty(level, be.getBlockPos());
		}
	}

	private static Predicate<UUID> onlinePlayers(PortableSpeakerBlockEntity be, UUID leaving) {
		MinecraftServer server = be.getLevel().getServer();
		return id -> !id.equals(leaving) && server.getPlayerList().getPlayer(id) != null;
	}

	private static SpeakerQueue queueOf(PortableSpeakerBlockEntity be) {
		return be.data().queue();
	}

	private static boolean isPlaying(PortableSpeakerBlockEntity be) {
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

	/** A placed speaker the player can reach, or null. */
	private static PortableSpeakerBlockEntity speakerInReach(ServerPlayer player, BlockPos pos) {
		if (!(player.level() instanceof ServerLevel level) || !level.isLoaded(pos)
				|| !player.isWithinBlockInteractionRange(pos, 1.0)) {
			return null;
		}
		return level.getBlockEntity(pos) instanceof PortableSpeakerBlockEntity be ? be : null;
	}
}
