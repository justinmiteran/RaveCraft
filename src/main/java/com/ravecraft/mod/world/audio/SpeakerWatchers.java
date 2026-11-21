package com.ravecraft.mod.world.audio;

import com.ravecraft.mod.network.Net;
import com.ravecraft.mod.network.payload.SpeakerClosedPayload;
import com.ravecraft.mod.network.payload.SpeakerStatePayload;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Who has which speaker's screen open. They alone receive its {@link SpeakerStatePayload}: once on opening, then
 * after each change, at most once per tick. A watcher who can no longer control the speaker (gone, or out of reach)
 * is dropped and told to close the screen. Server thread only.
 */
public final class SpeakerWatchers {
	private static final Map<UUID, Set<UUID>> WATCHERS = new HashMap<>();
	private static final Set<UUID> DIRTY = new HashSet<>();

	private SpeakerWatchers() {
	}

	public static void watch(ServerPlayer player, UUID speakerId, boolean open) {
		if (!open) {
			unwatch(speakerId, player.getUUID());
			return;
		}
		if (SpeakerHosts.handle(player.level().getServer(), speakerId).filter(h -> h.canControl(player)).isPresent()) {
			WATCHERS.computeIfAbsent(speakerId, k -> new HashSet<>()).add(player.getUUID());
			DIRTY.add(speakerId);
		} else {
			// Unknown or out of reach: no state will ever come, so the screen must not stay open empty.
			Net.send(player, new SpeakerClosedPayload(speakerId));
		}
	}

	public static void markDirty(UUID speakerId) {
		if (WATCHERS.containsKey(speakerId)) {
			DIRTY.add(speakerId);
		}
	}

	/** Whether speaker {@code speakerId} has a state waiting to be sent to its watchers. */
	public static boolean isDirty(UUID speakerId) {
		return DIRTY.contains(speakerId);
	}

	/** Whether {@code player} has the screen of speaker {@code speakerId} open; for tests and diagnostics. */
	public static boolean isWatching(UUID speakerId, UUID player) {
		Set<UUID> watchers = WATCHERS.get(speakerId);
		return watchers != null && watchers.contains(player);
	}

	public static void markAllDirty() {
		DIRTY.addAll(WATCHERS.keySet());
	}

	/** Sends each changed speaker's state to its watchers. Called once per server tick. */
	public static void flush(MinecraftServer server) {
		if (DIRTY.isEmpty()) {
			return;
		}
		for (UUID id : DIRTY) {
			Set<UUID> watchers = WATCHERS.get(id);
			if (watchers == null) {
				continue;
			}
			Optional<SpeakerHandle> handle = SpeakerHosts.handle(server, id);
			SpeakerStatePayload state = handle.map(SpeakerPlayback::stateOf).orElse(null);
			for (Iterator<UUID> it = watchers.iterator(); it.hasNext(); ) {
				ServerPlayer player = server.getPlayerList().getPlayer(it.next());
				if (player == null) {
					it.remove();
				} else if (state == null || !handle.get().canControl(player)) {
					it.remove();
					Net.send(player, new SpeakerClosedPayload(id));
				} else {
					Net.send(player, state);
				}
			}
			if (watchers.isEmpty()) {
				WATCHERS.remove(id);
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

	private static void unwatch(UUID speakerId, UUID player) {
		Set<UUID> watchers = WATCHERS.get(speakerId);
		if (watchers != null && watchers.remove(player) && watchers.isEmpty()) {
			WATCHERS.remove(speakerId);
		}
	}
}
