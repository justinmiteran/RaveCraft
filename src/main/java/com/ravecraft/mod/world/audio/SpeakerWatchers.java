package com.ravecraft.mod.world.audio;

import com.ravecraft.mod.blockentity.PortableSpeakerBlockEntity;
import com.ravecraft.mod.core.queue.QueueEntry;
import com.ravecraft.mod.core.queue.RepeatMode;
import com.ravecraft.mod.core.queue.SpeakerQueue;
import com.ravecraft.mod.core.speaker.SpeakerSettings;
import com.ravecraft.mod.network.Net;
import com.ravecraft.mod.network.payload.SpeakerStatePayload;
import com.ravecraft.mod.network.payload.SpeakerStatePayload.StateEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Who has which speaker's screen open. They alone receive its {@link SpeakerStatePayload}: once on opening, then
 * after each change, at most once per tick. A watcher who walked out of reach is dropped. Server thread only.
 */
public final class SpeakerWatchers {
	private static final Map<SpeakerKey, Set<UUID>> WATCHERS = new HashMap<>();
	private static final Set<SpeakerKey> DIRTY = new HashSet<>();

	private SpeakerWatchers() {
	}

	/** {@code player} opened ({@code open}) or closed the screen of the speaker at {@code pos}. */
	public static void watch(ServerPlayer player, BlockPos pos, boolean open) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		SpeakerKey key = new SpeakerKey(level.dimension(), pos.immutable());
		if (!open) {
			unwatch(key, player.getUUID());
			return;
		}
		if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof PortableSpeakerBlockEntity
				&& player.isWithinBlockInteractionRange(pos, 1.0)) {
			WATCHERS.computeIfAbsent(key, k -> new HashSet<>()).add(player.getUUID());
			DIRTY.add(key);
		}
	}

	/** The speaker at {@code pos} changed: its watchers get its state at the next flush. */
	public static void markDirty(ServerLevel level, BlockPos pos) {
		SpeakerKey key = new SpeakerKey(level.dimension(), pos);
		if (WATCHERS.containsKey(key)) {
			DIRTY.add(key);
		}
	}

	/** Whether the speaker at {@code pos} has a state waiting to be sent to its watchers. */
	public static boolean isDirty(ServerLevel level, BlockPos pos) {
		return DIRTY.contains(new SpeakerKey(level.dimension(), pos));
	}

	/** Every watched speaker changed: their watchers get their state at the next flush. */
	public static void markAllDirty() {
		DIRTY.addAll(WATCHERS.keySet());
	}

	/** Sends each changed speaker's state to its watchers. Called once per server tick. */
	public static void flush(MinecraftServer server) {
		if (DIRTY.isEmpty()) {
			return;
		}
		for (SpeakerKey key : DIRTY) {
			Set<UUID> watchers = WATCHERS.get(key);
			ServerLevel level = server.getLevel(key.dimension());
			if (watchers == null || level == null) {
				WATCHERS.remove(key);
				continue;
			}
			send(server, level, key.pos(), watchers);
			if (watchers.isEmpty()) {
				WATCHERS.remove(key);
			}
		}
		DIRTY.clear();
	}

	/** Sends the state of the speaker at {@code pos} to its watchers still in reach, forgetting the others. */
	private static void send(MinecraftServer server, ServerLevel level, BlockPos pos, Set<UUID> watchers) {
		SpeakerStatePayload state = stateOf(level, pos);
		for (Iterator<UUID> it = watchers.iterator(); it.hasNext(); ) {
			ServerPlayer player = server.getPlayerList().getPlayer(it.next());
			if (player == null || player.level() != level || !player.isWithinBlockInteractionRange(pos, 1.0)) {
				it.remove();
			} else {
				Net.send(player, state);
			}
		}
	}

	/** {@code player} left: they watch nothing any more. */
	public static void forget(UUID player) {
		WATCHERS.values().forEach(set -> set.remove(player));
		WATCHERS.values().removeIf(Set::isEmpty);
	}

	/** Forgets every watcher (server stop). */
	public static void clear() {
		WATCHERS.clear();
		DIRTY.clear();
	}

	/** What watchers of the speaker at {@code pos} see. */
	static SpeakerStatePayload stateOf(ServerLevel level, BlockPos pos) {
		if (!(level.getBlockEntity(pos) instanceof PortableSpeakerBlockEntity be)) {
			return new SpeakerStatePayload(pos, false, Optional.empty(), 0, List.of(), RepeatMode.ALL, false,
					SpeakerSettings.DEFAULT_VOLUME, false, -1);
		}
		SpeakerQueue queue = SpeakerPlayback.liveQueue(be);
		MinecraftServer server = level.getServer();
		List<StateEntry> entries = new ArrayList<>();
		for (QueueEntry entry : queue.displayOrder()) {
			boolean online = server.getPlayerList().getPlayer(entry.owner()) != null;
			entries.add(new StateEntry(entry.entryId(), entry.ownerName(), entry.title(), online, entry.durationMs()));
		}
		return new SpeakerStatePayload(pos, SpeakerPlayback.isPlaying(be), queue.current(), queue.positionMs(), entries,
				queue.repeat(), queue.shuffle(), be.data().settings().volume(), be.data().settings().powered(),
				queue.nextRoundIndex());
	}

	private static void unwatch(SpeakerKey key, UUID player) {
		Set<UUID> watchers = WATCHERS.get(key);
		if (watchers != null && watchers.remove(player) && watchers.isEmpty()) {
			WATCHERS.remove(key);
		}
	}
}
