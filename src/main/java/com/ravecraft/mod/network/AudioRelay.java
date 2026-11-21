package com.ravecraft.mod.network;

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
import com.ravecraft.mod.world.audio.SpeakerHandle;
import com.ravecraft.mod.world.audio.SpeakerHosts;
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
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Server side of the audio relay: checks each chunk and forwards each chunk from the session
 * owner to the players near the speaker. Listeners come from vanilla tracking of the speaker's host, which is
 * maintained on events, so nothing is scanned per tick.
 */
public final class AudioRelay {
	private static final Map<UUID, TokenBucket> BUCKETS = new HashMap<>();

	private AudioRelay() {
	}

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

	private static void relay(ServerPlayer sender, AudioChunkPayload chunk) {
		Optional<Session<UUID>> found = StreamSessions.table().byId(chunk.sessionId());
		if (!chunk.isWellFormed() || found.isEmpty() || !found.get().owner().equals(sender.getUUID())) {
			return;
		}
		long now = System.nanoTime();
		TokenBucket bucket = BUCKETS.computeIfAbsent(sender.getUUID(),
				id -> new TokenBucket(StreamFormat.RATE_BYTES_PER_SECOND, StreamFormat.RATE_BURST_BYTES, now));
		if (!bucket.tryConsume(chunk.cost(), now)) {
			return;
		}
		Session<UUID> session = found.get();
		SpeakerPlayback.onChunk(chunk.sessionId());
		Optional<SpeakerHandle> host = SpeakerHosts.handle(sender.level().getServer(), session.key());
		// A speaker whose host is lost (in its grace) plays nowhere: its chunks are dropped.
		if (host.isEmpty()) {
			return;
		}
		SpeakerHandle speaker = host.get();
		RelayedChunkPayload relayed = new RelayedChunkPayload(chunk.sessionId(), speaker.id(), chunk.seq(), chunk.frames());
		for (ServerPlayer listener : listeners(speaker.level(), speaker.position(), session.owner(), speaker.trackers(),
				relayRadius(speaker))) {
			// Where it plays and what, before the first chunk: a listener walking into range mid-track learns both.
			SpeakerHosts.tellHost(listener, chunk.sessionId());
			NowPlaying.tell(listener, chunk.sessionId());
			Net.send(listener, relayed);
		}
	}

	/**
	 * How far {@code speaker} is relayed: its profile range at its current volume, plus a margin. 0 when it is
	 * unpowered.
	 */
	public static double relayRadius(SpeakerHandle speaker) {
		if (!speaker.powered()) {
			return 0;
		}
		double volume = speaker.data().settings().volume() / (double) SpeakerSettings.MAX_VOLUME;
		return Propagation.relayRadius(AcousticConfig.get(), AcousticConfig.get().portable(), volume);
	}

	/** The candidates in {@code level} within {@code radius} of {@code centre} (the speaker), owner excluded. */
	public static List<ServerPlayer> listeners(ServerLevel level, Vec3 centre, UUID owner, Collection<ServerPlayer> candidates,
			double radius) {
		if (radius <= 0) {
			return List.of();
		}
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
