package com.ravecraft.mod.world.audio;

import com.ravecraft.mod.block.PortableSpeakerBlock;
import com.ravecraft.mod.core.audio.stream.SessionTable;
import com.ravecraft.mod.core.audio.stream.SessionTable.Opened;
import com.ravecraft.mod.core.audio.stream.SessionTable.Session;
import com.ravecraft.mod.network.Net;
import com.ravecraft.mod.network.payload.SessionEndPayload;
import com.ravecraft.mod.network.payload.StreamOrderPayload;
import com.ravecraft.mod.registry.ModBlocks;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The stream sessions of the running server: at most one per placed speaker, owned by the player streaming into it.
 * Server thread only. Sessions live in memory and end with the speaker, its power, its chunk or its owner.
 */
public final class StreamSessions {
	private static final SessionTable<SpeakerKey> TABLE = new SessionTable<>();

	private StreamSessions() {
	}

	/** Read access for the relay and the tests. */
	public static SessionTable<SpeakerKey> table() {
		return TABLE;
	}

	/**
	 * Opens a session for {@code player} on the powered speaker at {@code pos} (absolute), replacing any other
	 * stream there, and orders the player to start. A player must be within reach, except when renewing its own
	 * session for the next track of its queue: that one finishes with its tail played out. An invalid request is
	 * refused, and the refusal sent back carrying {@code request}.
	 */
	public static Optional<Session<SpeakerKey>> open(ServerPlayer player, BlockPos pos, int request) {
		if (!(player.level() instanceof ServerLevel level) || !level.isLoaded(pos)) {
			refuse(player, pos, request);
			return Optional.empty();
		}
		SpeakerKey key = new SpeakerKey(level.dimension(), pos);
		UUID id = player.getUUID();
		boolean renewing = TABLE.byKey(key).map(s -> s.owner().equals(id)).orElse(false);
		BlockState state = level.getBlockState(pos);
		if (!state.is(ModBlocks.PORTABLE_SPEAKER) || !state.getValue(PortableSpeakerBlock.POWERED)
				|| (!renewing && !player.isWithinBlockInteractionRange(pos, 1.0))) {
			refuse(player, pos, request);
			return Optional.empty();
		}
		Opened<SpeakerKey> opened = TABLE.open(key, id);
		opened.replaced().ifPresent(old -> {
			boolean sameOwner = old.owner().equals(id);
			notifyEnded(level.getServer(), old, !sameOwner, sameOwner);
		});
		Session<SpeakerKey> session = opened.session();
		Net.send(player, new StreamOrderPayload(session.id(), pos, true, request));
		return Optional.of(session);
	}

	private static void refuse(ServerPlayer player, BlockPos pos, int request) {
		Net.send(player, new StreamOrderPayload(StreamOrderPayload.NO_SESSION, pos, false, request));
	}

	/** Ends the session of the speaker at {@code pos} (absolute), if any: broken, switched off or unloaded. */
	public static void endAt(ServerLevel level, BlockPos pos) {
		TABLE.endByKey(new SpeakerKey(level.dimension(), pos)).ifPresent(s -> notifyEnded(level.getServer(), s, true, false));
	}

	/**
	 * Ends session {@code id} at the request of its owner; requests from anyone else are ignored. When the track
	 * {@code finished}, listeners play their buffered tail before stopping.
	 */
	public static void close(int id, ServerPlayer requester, boolean finished) {
		Optional<Session<SpeakerKey>> session = TABLE.byId(id);
		if (session.isPresent() && session.get().owner().equals(requester.getUUID())) {
			TABLE.endById(id);
			notifyEnded(requester.level().getServer(), session.get(), false, finished);
		}
	}

	/** Ends every session of a player who left. */
	public static void endByOwner(MinecraftServer server, UUID owner) {
		TABLE.endByOwner(owner).forEach(s -> notifyEnded(server, s, false, false));
	}

	/** Drops everything without notifying anyone: the server is stopping. */
	public static void clear() {
		TABLE.endAll();
	}

	/** Tells the players tracking the speaker that the session is over, and its owner to stop when asked to. */
	private static void notifyEnded(MinecraftServer server, Session<SpeakerKey> session, boolean tellOwner, boolean drain) {
		if (tellOwner) {
			ServerPlayer owner = server.getPlayerList().getPlayer(session.owner());
			if (owner != null) {
				Net.send(owner, new StreamOrderPayload(session.id(), session.key().pos(), false, StreamOrderPayload.NO_REQUEST));
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
