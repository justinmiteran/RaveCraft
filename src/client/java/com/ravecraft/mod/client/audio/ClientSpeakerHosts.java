package com.ravecraft.mod.client.audio;

import com.ravecraft.mod.core.speaker.CarryPlace;
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
	/** Height of the chest of a player carrying a speaker in its inventory or on its back, as a fraction of its height. */
	private static final double CHEST_HEIGHT = 0.6;
	private static final Map<UUID, SpeakerHostPayload> HOSTS = new LinkedHashMap<>();

	private ClientSpeakerHosts() {
	}

	/** Receives the host notices from the server, and forgets them all on disconnect. */
	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(SpeakerHostPayload.TYPE, (payload, context) -> put(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
	}

	/**
	 * Records where speaker {@code host.speakerId()} is, dropping the oldest notice beyond the cap.
	 *
	 * @param host the notice
	 */
	public static void put(SpeakerHostPayload host) {
		HOSTS.remove(host.speakerId());
		HOSTS.put(host.speakerId(), host);
		if (HOSTS.size() > MAX_HOSTS) {
			Iterator<UUID> oldest = HOSTS.keySet().iterator();
			oldest.next();
			oldest.remove();
		}
	}

	/**
	 * The last notice received for speaker {@code speakerId}, if any.
	 *
	 * @param speakerId the speaker
	 * @return the notice, or empty
	 */
	public static Optional<SpeakerHostPayload> get(UUID speakerId) {
		return Optional.ofNullable(HOSTS.get(speakerId));
	}

	/**
	 * Where speaker {@code speakerId} sounds from in {@code level}: its block centre, its carrier's eyes (in hand) or
	 * chest, or its item entity. Null when unknown, in another dimension, or its entity is not loaded here.
	 *
	 * @param speakerId the speaker
	 * @param level the client level, or null
	 * @return the position, or null
	 */
	public static @Nullable Vec3 position(UUID speakerId, @Nullable ClientLevel level) {
		SpeakerHostPayload host = HOSTS.get(speakerId);
		if (host == null || level == null || !level.dimension().equals(host.dimension())) {
			return null;
		}
		if (host.block().isPresent()) {
			return Vec3.atCenterOf(host.block().get());
		}
		return entityPosition(host, level.getEntity(host.entityId()));
	}

	/** Where a speaker held by {@code entity} sounds from: a carrier's eyes or chest, an item entity itself. */
	private static @Nullable Vec3 entityPosition(SpeakerHostPayload host, @Nullable Entity entity) {
		if (entity instanceof Player player) {
			return host.place().orElse(null) == CarryPlace.HAND
					? player.getEyePosition()
					: new Vec3(player.getX(), player.getY() + player.getBbHeight() * CHEST_HEIGHT, player.getZ());
		}
		return entity == null ? null : entity.position();
	}

	/** Forgets every host. */
	public static void clear() {
		HOSTS.clear();
	}
}
