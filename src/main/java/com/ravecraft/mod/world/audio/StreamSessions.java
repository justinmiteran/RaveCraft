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
	 * stream there, and orders the player to start. Returns empty, sending nothing, when the request is invalid.
	 */
	public static Optional<Session<SpeakerKey>> open(ServerPlayer player, BlockPos pos) {
		if (!(player.level() instanceof ServerLevel level) || !level.isLoaded(pos)) {
			return Optional.empty();
		}
		BlockState state = level.getBlockState(pos);
		if (!state.is(ModBlocks.PORTABLE_SPEAKER) || !state.getValue(PortableSpeakerBlock.POWERED)
				|| !player.isWithinBlockInteractionRange(pos, 1.0)) {
			return Optional.empty();
		}
		Opened<SpeakerKey> opened = TABLE.open(new SpeakerKey(level.dimension(), pos), player.getUUID());
		opened.replaced().ifPresent(old -> notifyEnded(level.getServer(), old, true));
		Session<SpeakerKey> session = opened.session();
		Net.send(player, new StreamOrderPayload(session.id(), session.key().pos(), true));
		return Optional.of(session);
	}

	/** Ends the session of the speaker at {@code pos} (absolute), if any: broken, switched off or unloaded. */
	public static void endAt(ServerLevel level, BlockPos pos) {
		TABLE.endByKey(new SpeakerKey(level.dimension(), pos)).ifPresent(s -> notifyEnded(level.getServer(), s, true));
	}

	/** Ends session {@code id} at the request of its owner; requests from anyone else are ignored. */
	public static void close(int id, ServerPlayer requester) {
		Optional<Session<SpeakerKey>> session = TABLE.byId(id);
		if (session.isPresent() && session.get().owner().equals(requester.getUUID())) {
			TABLE.endById(id);
			notifyEnded(requester.level().getServer(), session.get(), false);
		}
	}

	/** Ends every session of a player who left. */
	public static void endByOwner(MinecraftServer server, UUID owner) {
		TABLE.endByOwner(owner).forEach(s -> notifyEnded(server, s, false));
	}

	/** Drops everything without notifying anyone: the server is stopping. */
	public static void clear() {
		TABLE.endAll();
	}

	/** Tells the players tracking the speaker that the session is over, and its owner to stop when asked to. */
	private static void notifyEnded(MinecraftServer server, Session<SpeakerKey> session, boolean tellOwner) {
		if (tellOwner) {
			ServerPlayer owner = server.getPlayerList().getPlayer(session.owner());
			if (owner != null) {
				Net.send(owner, new StreamOrderPayload(session.id(), session.key().pos(), false));
			}
		}
		ServerLevel level = server.getLevel(session.key().dimension());
		if (level == null) {
			return;
		}
		SessionEndPayload end = new SessionEndPayload(session.id());
		for (ServerPlayer player : PlayerLookup.tracking(level, session.key().pos())) {
			if (!player.getUUID().equals(session.owner())) {
				Net.send(player, end);
			}
		}
	}
}
