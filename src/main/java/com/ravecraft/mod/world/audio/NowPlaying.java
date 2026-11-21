package com.ravecraft.mod.world.audio;

import com.ravecraft.mod.core.audio.stream.SessionNotices;
import com.ravecraft.mod.core.queue.QueueEntry;
import com.ravecraft.mod.network.Net;
import com.ravecraft.mod.network.payload.NowPlayingPayload;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * What each open session plays, told once to each player who hears it: the owner when it starts, a listener before
 * its first relayed chunk (so players walking into range mid-track learn it too). Server thread only.
 */
public final class NowPlaying {
	private static final SessionNotices<NowPlayingPayload> NOTICES = new SessionNotices<>();

	private NowPlaying() {
	}

	/** Session {@code sessionId} starts streaming {@code entry}: remembers it and tells the owner. */
	public static void start(ServerPlayer owner, int sessionId, UUID speakerId, QueueEntry entry) {
		NOTICES.open(sessionId, NowPlayingPayload.of(sessionId, speakerId, entry));
		tell(owner, sessionId);
	}

	/** Tells {@code player} what session {@code sessionId} plays, unless they were already told. */
	public static void tell(ServerPlayer player, int sessionId) {
		NOTICES.firstFor(sessionId, player.getUUID()).ifPresent(notice -> Net.send(player, notice));
	}

	/** The notice of session {@code sessionId}, while it plays. */
	public static Optional<NowPlayingPayload> of(int sessionId) {
		return NOTICES.get(sessionId);
	}

	/** {@code player} left: tell them again if they come back while the same sessions play. */
	public static void forget(UUID player) {
		NOTICES.forget(player);
	}

	/** Session {@code sessionId} stopped: forgets its notice. */
	public static void end(int sessionId) {
		NOTICES.end(sessionId);
	}

	/** Forgets every notice (server stop). */
	public static void clear() {
		NOTICES.clear();
	}
}
