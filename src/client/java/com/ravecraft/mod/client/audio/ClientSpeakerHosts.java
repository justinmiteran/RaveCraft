package com.ravecraft.mod.client.audio;

import com.ravecraft.mod.network.payload.SpeakerHostPayload;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Where each speaker heard by this client plays from, as the server last said ({@link SpeakerHostPayload}): a block,
 * or an entity whose position is read live. Client thread only.
 */
public final class ClientSpeakerHosts {
	/** Hosts kept at most; the oldest told is forgotten first (a speaker still playing is told again on change). */
	private static final int MAX_HOSTS = 256;
	/** Height of the chest of a player carrying a speaker outside its hands, as a fraction of its height. */
	private static final double CHEST_HEIGHT = 0.6;
	private static final Map<UUID, SpeakerHostPayload> HOSTS = new LinkedHashMap<>();

	private ClientSpeakerHosts() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(SpeakerHostPayload.TYPE, (payload, context) -> put(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
	}

	public static void put(SpeakerHostPayload host) {
		HOSTS.remove(host.speakerId());
		HOSTS.put(host.speakerId(), host);
		if (HOSTS.size() > MAX_HOSTS) {
			Iterator<UUID> oldest = HOSTS.keySet().iterator();
			oldest.next();
			oldest.remove();
		}
	}

	public static Optional<SpeakerHostPayload> get(UUID speakerId) {
		return Optional.ofNullable(HOSTS.get(speakerId));
	}

	/**
	 * Where speaker {@code speakerId} sounds from in {@code level}: its block centre, its carrier's eyes (in hand) or
	 * chest, or its item entity. Null when unknown, in another dimension, or its entity is not loaded here.
	 */
	public static @Nullable Vec3 position(UUID speakerId, @Nullable ClientLevel level) {
		SpeakerHostPayload host = HOSTS.get(speakerId);
		if (host == null || level == null || !level.dimension().equals(host.dimension())) {
			return null;
		}
		if (host.block().isPresent()) {
			return Vec3.atCenterOf(host.block().get());
		}
		Entity entity = level.getEntity(host.entityId());
		if (entity == null) {
			return null;
		}
		if (entity instanceof Player player) {
			return host.inHand()
					? player.getEyePosition()
					: new Vec3(player.getX(), player.getY() + player.getBbHeight() * CHEST_HEIGHT, player.getZ());
		}
		return entity.position();
	}

	public static void clear() {
		HOSTS.clear();
	}
}
