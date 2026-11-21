package com.ravecraft.mod.world.audio;

import com.ravecraft.mod.core.audio.stream.SessionTable;
import com.ravecraft.mod.core.audio.stream.SessionTable.Session;
import com.ravecraft.mod.network.Net;
import com.ravecraft.mod.network.payload.SessionEndPayload;
import com.ravecraft.mod.network.payload.StreamOrderPayload;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The stream sessions of the running server: at most one per speaker id, each streaming one queue entry from its
 * owner's PC. Only {@link SpeakerPlayback} opens them. Server thread only; sessions live in memory.
 */
public final class StreamSessions {
	private static final SessionTable<UUID> TABLE = new SessionTable<>();
	/** The queue entry each open session streams, so stop orders can name it. */
	private static final Map<Integer, UUID> ENTRIES = new HashMap<>();
	/**
	 * Where each open session's speaker was when it opened: the stop order and the end notice still need it once the
	 * speaker is gone (broken, unloaded). Block hosts only until the streaming payloads carry the id.
	 */
	private static final Map<Integer, SpeakerHost.Block> HOSTS = new HashMap<>();

	private StreamSessions() {
	}

	/** Read access for the relay and the tests. */
	public static SessionTable<UUID> table() {
		return TABLE;
	}

	/**
	 * Opens a session for {@code owner} to stream {@code entryId} into the powered speaker {@code handle}, cutting any
	 * other stream there. Distance does not matter: the owner may be anywhere. Sends no order.
	 */
	public static Optional<Session<UUID>> open(SpeakerHandle handle, ServerPlayer owner, UUID entryId) {
		if (!handle.powered() || !(handle.host() instanceof SpeakerHost.Block block)) {
			return Optional.empty();
		}
		endAt(handle.level().getServer(), handle.id());
		Session<UUID> session = TABLE.open(handle.id(), owner.getUUID()).session();
		ENTRIES.put(session.id(), entryId);
		HOSTS.put(session.id(), block);
		return Optional.of(session);
	}

	/** Ends the session of speaker {@code speakerId}, if any, cutting it at once everywhere. */
	public static Optional<Session<UUID>> endAt(MinecraftServer server, UUID speakerId) {
		Optional<Session<UUID>> ended = TABLE.endByKey(speakerId);
		ended.ifPresent(s -> notifyEnded(server, s, true, false));
		return ended;
	}

	/**
	 * Ends session {@code id} at the request of its owner; requests from anyone else are ignored and return empty.
	 * When the track {@code finished}, listeners play their buffered tail before stopping.
	 */
	public static Optional<Session<UUID>> close(int id, ServerPlayer requester, boolean finished) {
		Optional<Session<UUID>> session = TABLE.byId(id);
		if (session.isEmpty() || !session.get().owner().equals(requester.getUUID())) {
			return Optional.empty();
		}
		TABLE.endById(id);
		notifyEnded(requester.level().getServer(), session.get(), false, finished);
		return session;
	}

	/** Ends every session of a player who left, without telling them. */
	public static List<Session<UUID>> endByOwner(MinecraftServer server, UUID owner) {
		List<Session<UUID>> ended = TABLE.endByOwner(owner);
		ended.forEach(s -> notifyEnded(server, s, false, false));
		return ended;
	}

	/** Drops everything without notifying anyone: the server is stopping. */
	public static void clear() {
		TABLE.endAll();
		ENTRIES.clear();
		HOSTS.clear();
		NowPlaying.clear();
	}

	/** Tells the players tracking the speaker that the session is over, and its owner to stop when asked to. */
	private static void notifyEnded(MinecraftServer server, Session<UUID> session, boolean tellOwner, boolean drain) {
		NowPlaying.end(session.id());
		UUID entryId = ENTRIES.remove(session.id());
		SpeakerHost.Block host = HOSTS.remove(session.id());
		if (host == null) {
			return;
		}
		if (tellOwner && entryId != null) {
			ServerPlayer owner = server.getPlayerList().getPlayer(session.owner());
			if (owner != null) {
				Net.send(owner, new StreamOrderPayload(session.id(), host.dimension(), host.pos(), false, entryId, 0));
			}
		}
		ServerLevel level = server.getLevel(host.dimension());
		if (level == null) {
			return;
		}
		SessionEndPayload end = new SessionEndPayload(session.id(), drain);
		for (ServerPlayer player : PlayerLookup.tracking(level, host.pos())) {
			if (!player.getUUID().equals(session.owner())) {
				Net.send(player, end);
			}
		}
	}
}
