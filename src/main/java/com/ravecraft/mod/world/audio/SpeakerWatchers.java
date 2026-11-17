package com.ravecraft.mod.world.audio;

import com.ravecraft.mod.blockentity.PortableSpeakerBlockEntity;
import com.ravecraft.mod.network.Net;
import com.ravecraft.mod.network.payload.SpeakerStatePayload;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
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

	public static void markDirty(ServerLevel level, BlockPos pos) {
		SpeakerKey key = new SpeakerKey(level.dimension(), pos);
		if (WATCHERS.containsKey(key)) {
			DIRTY.add(key);
		}
	}

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
			SpeakerStatePayload state = SpeakerPlayback.stateOf(level, key.pos());
			for (Iterator<UUID> it = watchers.iterator(); it.hasNext(); ) {
				ServerPlayer player = server.getPlayerList().getPlayer(it.next());
				if (player == null || player.level() != level || !player.isWithinBlockInteractionRange(key.pos(), 1.0)) {
					it.remove();
				} else {
					Net.send(player, state);
				}
			}
			if (watchers.isEmpty()) {
				WATCHERS.remove(key);
			}
		}
		DIRTY.clear();
	}

	public static void forget(UUID player) {
		WATCHERS.values().forEach(set -> set.remove(player));
		WATCHERS.values().removeIf(Set::isEmpty);
	}

	public static void clear() {
		WATCHERS.clear();
		DIRTY.clear();
	}

	private static void unwatch(SpeakerKey key, UUID player) {
		Set<UUID> watchers = WATCHERS.get(key);
		if (watchers != null && watchers.remove(player) && watchers.isEmpty()) {
			WATCHERS.remove(key);
		}
	}
}
