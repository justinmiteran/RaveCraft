package com.ravecraft.mod.network;

import com.ravecraft.mod.blockentity.PortableSpeakerBlockEntity;
import com.ravecraft.mod.config.AcousticConfig;
import com.ravecraft.mod.core.acoustics.Propagation;
import com.ravecraft.mod.core.speaker.SpeakerSettings;
import com.ravecraft.mod.core.audio.stream.SessionTable.Session;
import com.ravecraft.mod.core.audio.stream.StreamFormat;
import com.ravecraft.mod.core.audio.stream.TokenBucket;
import com.ravecraft.mod.network.payload.AudioChunkPayload;
import com.ravecraft.mod.network.payload.RelayedChunkPayload;
import com.ravecraft.mod.world.audio.NowPlaying;
import com.ravecraft.mod.network.payload.StreamClosePayload;
import com.ravecraft.mod.world.audio.SpeakerKey;
import com.ravecraft.mod.world.audio.SpeakerPlayback;
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
 * Server side of the audio relay: checks each chunk and forwards each chunk from the session
 * owner to the players near the speaker. Listeners come from vanilla chunk tracking, which is maintained on events,
 * so nothing is scanned per tick.
 */
public final class AudioRelay {
	private static final Map<UUID, TokenBucket> BUCKETS = new HashMap<>();

	private AudioRelay() {
	}

	/** Registers the audio and stream-close receivers and the cleanup of their rate limits. */
	public static void init() {
		ServerPlayNetworking.registerGlobalReceiver(StreamClosePayload.TYPE,
				(payload, context) -> SpeakerPlayback.onStreamClose(context.player(), payload.sessionId(), payload.finished()));
		ServerPlayNetworking.registerGlobalReceiver(AudioChunkPayload.TYPE,
				(payload, context) -> relay(context.player(), payload));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			BUCKETS.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> BUCKETS.clear());
	}

	/** Forwards a well-formed chunk from its session's owner, within the sender's rate limit. */
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
		SpeakerPlayback.onChunk(chunk.sessionId());
		ServerLevel level = sender.level().getServer().getLevel(session.key().dimension());
		if (level == null) {
			return;
		}
		BlockPos pos = session.key().pos();
		RelayedChunkPayload relayed = new RelayedChunkPayload(chunk.sessionId(), pos, chunk.seq(), chunk.frames());
		double radius = relayRadius(level, pos);
		for (ServerPlayer listener : listeners(level, pos, session.owner(), PlayerLookup.tracking(level, pos), radius)) {
			NowPlaying.tell(listener, chunk.sessionId());
			Net.send(listener, relayed);
		}
	}

	/**
	 * How far the speaker at {@code pos} is relayed: its profile range at its current volume, plus a margin. 0 when
	 * there is no powered speaker there.
	 *
	 * @param level the speaker's level
	 * @param pos where the speaker stands
	 * @return the radius, in blocks
	 */
	public static double relayRadius(ServerLevel level, BlockPos pos) {
		if (!(level.getBlockEntity(pos) instanceof PortableSpeakerBlockEntity speaker) || !speaker.data().settings().powered()) {
			return 0;
		}
		double volume = speaker.data().settings().volume() / (double) SpeakerSettings.MAX_VOLUME;
		return Propagation.relayRadius(AcousticConfig.get(), AcousticConfig.get().portable(), volume);
	}

	/**
	 * The candidates in {@code level} within {@code radius} of the speaker, owner excluded.
	 *
	 * @param level the speaker's level
	 * @param pos where the speaker stands
	 * @param owner the player streaming
	 * @param candidates the players to filter
	 * @param radius the relay radius, in blocks
	 * @return the players to relay to
	 */
	public static List<ServerPlayer> listeners(ServerLevel level, BlockPos pos, UUID owner, Collection<ServerPlayer> candidates,
			double radius) {
		if (radius <= 0) {
			return List.of();
		}
		Vec3 centre = Vec3.atCenterOf(pos);
		double rangeSq = radius * radius;
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
