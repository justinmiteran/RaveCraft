package com.ravecraft.mod.network;

import com.ravecraft.mod.core.audio.stream.TokenBucket;
import com.ravecraft.mod.network.payload.QueueAddPayload;
import com.ravecraft.mod.network.payload.SpeakerCommandPayload;
import com.ravecraft.mod.network.payload.SpeakerCommandPayload.Command;
import com.ravecraft.mod.network.payload.SpeakerWatchPayload;
import com.ravecraft.mod.network.payload.StreamResultPayload;
import com.ravecraft.mod.network.payload.StreamStartedPayload;
import com.ravecraft.mod.world.audio.NowPlaying;
import com.ravecraft.mod.world.audio.SpeakerHosts;
import com.ravecraft.mod.world.audio.SpeakerPlayback;
import com.ravecraft.mod.world.audio.SpeakerWatchers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/** Server side of the shared queues: receives queue packets and drives {@link SpeakerPlayback} from events. */
public final class SpeakerNetwork {
	/** At most this many {@link QueueAddPayload} per second and per player; extra ones are dropped. */
	private static final long ADDS_PER_SECOND = 4;
	private static final Map<UUID, TokenBucket> ADD_BUCKETS = new HashMap<>();
	/**
	 * At most this many {@link SpeakerCommandPayload} per second and per player: each PLAY makes the entry's owner
	 * start a decoder, so a spammer must not be able to churn another player's PC.
	 */
	public static final long COMMANDS_PER_SECOND = 10;
	private static final Map<UUID, TokenBucket> COMMAND_BUCKETS = new HashMap<>();
	/** VOLUME commands have their own budget, so dragging the slider does not starve the other buttons. */
	public static final long VOLUMES_PER_SECOND = 8;
	private static final Map<UUID, TokenBucket> VOLUME_BUCKETS = new HashMap<>();
	/** At most this many {@link StreamStartedPayload} per second and per player. */
	private static final long STARTS_PER_SECOND = 10;
	private static final Map<UUID, TokenBucket> START_BUCKETS = new HashMap<>();

	private SpeakerNetwork() {
	}

	public static void init() {
		ServerPlayNetworking.registerGlobalReceiver(QueueAddPayload.TYPE, (payload, context) -> {
			if (allowAdd(context.player())) {
				SpeakerPlayback.add(context.player(), payload.speakerId(), payload.entries());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(SpeakerCommandPayload.TYPE, (payload, context) -> {
			boolean allowed = payload.command() == Command.VOLUME ? allowVolume(context.player()) : allowCommand(context.player());
			if (allowed) {
				SpeakerPlayback.command(context.player(), payload.speakerId(), payload.command(), payload.entryId(), payload.value());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(StreamStartedPayload.TYPE, (payload, context) -> {
			if (consume(START_BUCKETS, context.player(), STARTS_PER_SECOND)) {
				SpeakerPlayback.onStreamStarted(context.player(), payload.sessionId(), payload.durationMs());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(SpeakerWatchPayload.TYPE, (payload, context) ->
				SpeakerWatchers.watch(context.player(), payload.speakerId(), payload.open()));
		ServerPlayNetworking.registerGlobalReceiver(StreamResultPayload.TYPE, (payload, context) ->
				SpeakerPlayback.onStreamResult(context.player(), payload.sessionId()));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> SpeakerPlayback.onJoin(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.player.getUUID();
			ADD_BUCKETS.remove(id);
			COMMAND_BUCKETS.remove(id);
			VOLUME_BUCKETS.remove(id);
			START_BUCKETS.remove(id);
			SpeakerWatchers.forget(id);
			NowPlaying.forget(id);
			SpeakerHosts.forget(id);
			SpeakerPlayback.onDisconnect(server, id);
		});
		SpeakerHosts.init();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			SpeakerHosts.tick(server);
			SpeakerPlayback.tick(server);
			SpeakerWatchers.flush(server);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			SpeakerPlayback.pauseAll(server);
			SpeakerHosts.clear();
			SpeakerWatchers.clear();
			ADD_BUCKETS.clear();
			COMMAND_BUCKETS.clear();
			VOLUME_BUCKETS.clear();
			START_BUCKETS.clear();
		});
	}

	/** Consumes one command token of {@code player}; false when over {@link #COMMANDS_PER_SECOND}. */
	public static boolean allowCommand(ServerPlayer player) {
		return consume(COMMAND_BUCKETS, player, COMMANDS_PER_SECOND);
	}

	/** Consumes one VOLUME token of {@code player}; false when over {@link #VOLUMES_PER_SECOND}. */
	public static boolean allowVolume(ServerPlayer player) {
		return consume(VOLUME_BUCKETS, player, VOLUMES_PER_SECOND);
	}

	private static boolean allowAdd(ServerPlayer player) {
		return consume(ADD_BUCKETS, player, ADDS_PER_SECOND);
	}

	private static boolean consume(Map<UUID, TokenBucket> buckets, ServerPlayer player, long perSecond) {
		long now = System.nanoTime();
		return buckets.computeIfAbsent(player.getUUID(), id -> new TokenBucket(perSecond, perSecond, now)).tryConsume(1, now);
	}
}
