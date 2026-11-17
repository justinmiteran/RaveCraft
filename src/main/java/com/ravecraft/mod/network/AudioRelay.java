package com.ravecraft.mod.network;

import com.ravecraft.mod.core.audio.stream.SessionTable.Session;
import com.ravecraft.mod.core.audio.stream.StreamFormat;
import com.ravecraft.mod.core.audio.stream.TokenBucket;
import com.ravecraft.mod.network.payload.AudioChunkPayload;
import com.ravecraft.mod.network.payload.RelayedChunkPayload;
import com.ravecraft.mod.network.payload.StreamClosePayload;
import com.ravecraft.mod.network.payload.StreamOpenPayload;
import com.ravecraft.mod.world.audio.SpeakerKey;
import com.ravecraft.mod.world.audio.StreamSessions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Server side of the audio relay: opens and closes sessions on request and forwards each chunk from the session
 * owner to the players near the speaker. Listeners come from vanilla chunk tracking, which is maintained on events,
 * so nothing is scanned per tick.
 */
public final class AudioRelay {
	private static final Map<UUID, TokenBucket> BUCKETS = new HashMap<>();

	private AudioRelay() {
	}

	public static void init() {
		ServerPlayNetworking.registerGlobalReceiver(StreamOpenPayload.TYPE,
				(payload, context) -> StreamSessions.open(context.player(), payload.pos(), payload.request()));
		ServerPlayNetworking.registerGlobalReceiver(StreamClosePayload.TYPE,
				(payload, context) -> StreamSessions.close(payload.sessionId(), context.player(), payload.finished()));
		ServerPlayNetworking.registerGlobalReceiver(AudioChunkPayload.TYPE,
				(payload, context) -> relay(context.player(), payload));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			BUCKETS.remove(handler.player.getUUID());
			StreamSessions.endByOwner(server, handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			BUCKETS.clear();
			StreamSessions.clear();
		});
	}

	private static void relay(ServerPlayer sender, AudioChunkPayload chunk) {
		Optional<Session<SpeakerKey>> found = StreamSessions.table().byId(chunk.sessionId());
		if (!chunk.isWellFormed() || found.isEmpty() || !found.get().owner().equals(sender.getUUID())) {
			return;
		}
		long now = System.nanoTime();
		TokenBucket bucket = BUCKETS.computeIfAbsent(sender.getUUID(),
				id -> new TokenBucket(StreamFormat.RATE_BYTES_PER_SECOND, StreamFormat.RATE_BURST_BYTES, now));
		if (!bucket.tryConsume(chunk.cost(), now)) {
			return;
		}
		Session<SpeakerKey> session = found.get();
		ServerLevel level = sender.level().getServer().getLevel(session.key().dimension());
		if (level == null) {
			return;
		}
		BlockPos pos = session.key().pos();
		RelayedChunkPayload relayed = new RelayedChunkPayload(chunk.sessionId(), pos, chunk.seq(), chunk.frames());
		for (ServerPlayer listener : listeners(level, pos, session.owner(), PlayerLookup.tracking(level, pos))) {
			Net.send(listener, relayed);
		}
	}

	/** The candidates in {@code level} within {@link StreamFormat#RELAY_RANGE} of the speaker, owner excluded. */
	public static List<ServerPlayer> listeners(ServerLevel level, BlockPos pos, UUID owner, Collection<ServerPlayer> candidates) {
		Vec3 centre = Vec3.atCenterOf(pos);
		double rangeSq = StreamFormat.RELAY_RANGE * StreamFormat.RELAY_RANGE;
		List<ServerPlayer> listeners = new ArrayList<>();
		for (ServerPlayer player : candidates) {
			if (player.level() == level && !player.getUUID().equals(owner)
					&& player.position().distanceToSqr(centre) <= rangeSq) {
				listeners.add(player);
			}
		}
		return listeners;
	}
}
