package com.ravecraft.mod.world.audio;

import com.ravecraft.mod.core.audio.stream.SessionNotices;
import com.ravecraft.mod.core.audio.stream.SessionTable.Session;
import com.ravecraft.mod.core.speaker.SpeakerKind;
import com.ravecraft.mod.network.Net;
import com.ravecraft.mod.network.payload.SpeakerHostPayload;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Where each open session's speaker is, its volume and power, told once to each player who hears it: rebuilt and sent
 * again to everyone hearing it on each change, and to a new listener before its first relayed chunk. Server thread.
 */
public final class SpeakerHostNotices {
	private static final SessionNotices<SpeakerHostPayload> NOTICES = new SessionNotices<>();

	private SpeakerHostNotices() {
	}

	/**
	 * (Re)builds the host notice of speaker {@code speakerId} while it plays, and sends it to the players tracking it
	 * and to the session owner. Called when the session starts and when the host, volume or power change; a no-op
	 * when it does not play or its host is not reachable.
	 *
	 * @param server the server
	 * @param speakerId the speaker
	 */
	public static void notice(MinecraftServer server, UUID speakerId) {
		Optional<Session<UUID>> session = StreamSessions.table().byKey(speakerId);
		Optional<SpeakerHandle> handle = SpeakerHosts.handle(server, speakerId);
		if (session.isEmpty() || handle.isEmpty()) {
			return;
		}
		int sessionId = session.get().id();
		NOTICES.open(sessionId, payloadOf(handle.get()));
		Set<ServerPlayer> hearers = new LinkedHashSet<>(handle.get().trackers());
		ServerPlayer owner = server.getPlayerList().getPlayer(session.get().owner());
		if (owner != null) {
			hearers.add(owner);
		}
		for (ServerPlayer player : hearers) {
			tellHost(player, sessionId);
		}
	}

	/**
	 * Sends the host of session {@code sessionId} to {@code player} unless already told; returns whether it sent.
	 *
	 * @param player the player
	 * @param sessionId the session
	 * @return true when the notice was sent
	 */
	public static boolean tellHost(ServerPlayer player, int sessionId) {
		Optional<SpeakerHostPayload> notice = NOTICES.firstFor(sessionId, player.getUUID());
		notice.ifPresent(payload -> Net.send(player, payload));
		return notice.isPresent();
	}

	/**
	 * The host notice of session {@code sessionId}, while it plays.
	 *
	 * @param sessionId the session
	 * @return the notice, or empty
	 */
	public static Optional<SpeakerHostPayload> noticeOf(int sessionId) {
		return NOTICES.get(sessionId);
	}

	/** Session {@code sessionId} ended: drops its notice and returns everyone told about it, who may be hearing it. */
	static Set<UUID> end(int sessionId) {
		Set<UUID> reached = NOTICES.reached(sessionId);
		NOTICES.end(sessionId);
		return reached;
	}

	/**
	 * {@code player} left: tell them again if they come back while the same sessions play.
	 *
	 * @param player the player
	 */
	public static void forget(UUID player) {
		NOTICES.forget(player);
	}

	/** Drops every notice without telling anyone: the sessions are all gone. */
	static void clear() {
		NOTICES.clear();
	}

	/** Where {@code handle}'s speaker is, as its listeners need it, with its kind, volume and power. */
	private static SpeakerHostPayload payloadOf(SpeakerHandle handle) {
		int volume = handle.data().settings().volume();
		boolean powered = handle.powered();
		SpeakerKind kind = handle.kind();
		return switch (handle.host()) {
			case SpeakerHost.Block block -> new SpeakerHostPayload(handle.id(), block.dimension(), Optional.of(block.pos()),
					SpeakerHostPayload.NO_ENTITY, kind, Optional.empty(), volume, powered);
			case SpeakerHost.Carrier carrier -> new SpeakerHostPayload(handle.id(), carrier.dimension(), Optional.empty(),
					entityId(handle.level().getServer().getPlayerList().getPlayer(carrier.player())), kind,
					Optional.of(carrier.place()), volume, powered);
			case SpeakerHost.Dropped dropped -> new SpeakerHostPayload(handle.id(), dropped.dimension(), Optional.empty(),
					entityId(handle.level().getEntity(dropped.entity())), kind, Optional.empty(), volume, powered);
		};
	}

	private static int entityId(@Nullable Entity entity) {
		return entity != null ? entity.getId() : SpeakerHostPayload.NO_ENTITY;
	}
}
