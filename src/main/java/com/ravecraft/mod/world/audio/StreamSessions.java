package com.ravecraft.mod.world.audio;

import com.ravecraft.mod.block.PortableSpeakerBlock;
import com.ravecraft.mod.core.audio.stream.SessionTable;
import com.ravecraft.mod.core.audio.stream.SessionTable.Session;
import com.ravecraft.mod.network.Net;
import com.ravecraft.mod.network.payload.SessionEndPayload;
import com.ravecraft.mod.network.payload.StreamOrderPayload;
import com.ravecraft.mod.registry.ModBlocks;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The stream sessions of the running server: at most one per placed speaker, each streaming one queue entry from
 * its owner's PC. Only {@link SpeakerPlayback} opens them. Server thread only; sessions live in memory.
 */
public final class StreamSessions {
	private static final SessionTable<SpeakerKey> TABLE = new SessionTable<>();
	/** The queue entry each open session streams, so stop orders can name it. */
	private static final Map<Integer, UUID> ENTRIES = new HashMap<>();

	private StreamSessions() {
	}

	/** Read access for the relay and the tests. */
	public static SessionTable<SpeakerKey> table() {
		return TABLE;
	}

	/**
	 * Opens a session for {@code owner} to stream {@code entryId} into the powered speaker at {@code pos} (absolute),
	 * cutting any other stream there. Distance does not matter: the owner may be anywhere. Sends no order.
	 */
	public static Optional<Session<SpeakerKey>> open(ServerLevel level, BlockPos pos, ServerPlayer owner, UUID entryId) {
		if (!level.isLoaded(pos)) {
			return Optional.empty();
		}
		BlockState state = level.getBlockState(pos);
		if (!state.is(ModBlocks.PORTABLE_SPEAKER) || !state.getValue(PortableSpeakerBlock.POWERED)) {
			return Optional.empty();
		}
		SpeakerKey key = new SpeakerKey(level.dimension(), pos.immutable());
		endAt(level, pos);
		Session<SpeakerKey> session = TABLE.open(key, owner.getUUID()).session();
		ENTRIES.put(session.id(), entryId);
		return Optional.of(session);
	}

	/** Ends the session of the speaker at {@code pos} (absolute), if any, cutting it at once everywhere. */
	public static Optional<Session<SpeakerKey>> endAt(ServerLevel level, BlockPos pos) {
		Optional<Session<SpeakerKey>> ended = TABLE.endByKey(new SpeakerKey(level.dimension(), pos));
		ended.ifPresent(s -> notifyEnded(level.getServer(), s, true, false));
		return ended;
	}

	/**
	 * Ends session {@code id} at the request of its owner; requests from anyone else are ignored and return empty.
	 * When the track {@code finished}, listeners play their buffered tail before stopping.
	 */
	public static Optional<Session<SpeakerKey>> close(int id, ServerPlayer requester, boolean finished) {
		Optional<Session<SpeakerKey>> session = TABLE.byId(id);
		if (session.isEmpty() || !session.get().owner().equals(requester.getUUID())) {
			return Optional.empty();
		}
		TABLE.endById(id);
		notifyEnded(requester.level().getServer(), session.get(), false, finished);
		return session;
	}

	/** Ends every session of a player who left, without telling them. */
	public static List<Session<SpeakerKey>> endByOwner(MinecraftServer server, UUID owner) {
		List<Session<SpeakerKey>> ended = TABLE.endByOwner(owner);
		ended.forEach(s -> notifyEnded(server, s, false, false));
		return ended;
	}

	/** Drops everything without notifying anyone: the server is stopping. */
	public static void clear() {
		TABLE.endAll();
		ENTRIES.clear();
		NowPlaying.clear();
	}

	/** Tells the players tracking the speaker that the session is over, and its owner to stop when asked to. */
	private static void notifyEnded(MinecraftServer server, Session<SpeakerKey> session, boolean tellOwner, boolean drain) {
		NowPlaying.end(session.id());
		UUID entryId = ENTRIES.remove(session.id());
		if (tellOwner && entryId != null) {
			ServerPlayer owner = server.getPlayerList().getPlayer(session.owner());
			if (owner != null) {
				Net.send(owner, new StreamOrderPayload(session.id(), session.key().dimension(), session.key().pos(), false, entryId, 0));
			}
		}
		ServerLevel level = server.getLevel(session.key().dimension());
		if (level == null) {
			return;
		}
		SessionEndPayload end = new SessionEndPayload(session.id(), drain);
		for (ServerPlayer player : PlayerLookup.tracking(level, session.key().pos())) {
			if (!player.getUUID().equals(session.owner())) {
				Net.send(player, end);
			}
		}
	}
}
