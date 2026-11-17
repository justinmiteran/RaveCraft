package com.ravecraft.mod.network;

import com.ravecraft.mod.core.audio.stream.TokenBucket;
import com.ravecraft.mod.network.payload.QueueAddPayload;
import com.ravecraft.mod.network.payload.SpeakerCommandPayload;
import com.ravecraft.mod.network.payload.SpeakerWatchPayload;
import com.ravecraft.mod.network.payload.StreamResultPayload;
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

	private SpeakerNetwork() {
	}

	public static void init() {
		ServerPlayNetworking.registerGlobalReceiver(QueueAddPayload.TYPE, (payload, context) -> {
			if (allowAdd(context.player())) {
				SpeakerPlayback.add(context.player(), payload.pos(), payload.entries());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(SpeakerCommandPayload.TYPE, (payload, context) ->
				SpeakerPlayback.command(context.player(), payload.pos(), payload.command(), payload.entryId()));
		ServerPlayNetworking.registerGlobalReceiver(SpeakerWatchPayload.TYPE, (payload, context) ->
				SpeakerWatchers.watch(context.player(), payload.pos(), payload.open()));
		ServerPlayNetworking.registerGlobalReceiver(StreamResultPayload.TYPE, (payload, context) ->
				SpeakerPlayback.onStreamResult(context.player(), payload.sessionId()));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> SpeakerPlayback.onJoin(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.player.getUUID();
			ADD_BUCKETS.remove(id);
			SpeakerWatchers.forget(id);
			SpeakerPlayback.onDisconnect(server, id);
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			SpeakerPlayback.tick(server);
			SpeakerWatchers.flush(server);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			SpeakerPlayback.pauseAll(server);
			SpeakerWatchers.clear();
			ADD_BUCKETS.clear();
		});
	}

	private static boolean allowAdd(ServerPlayer player) {
		long now = System.nanoTime();
		return ADD_BUCKETS.computeIfAbsent(player.getUUID(), id -> new TokenBucket(ADDS_PER_SECOND, ADDS_PER_SECOND, now))
				.tryConsume(1, now);
	}
}
