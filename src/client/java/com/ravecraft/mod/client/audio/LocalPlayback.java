package com.ravecraft.mod.client.audio;

import com.ravecraft.mod.client.audio.stream.ReceivedStreams;
import com.ravecraft.mod.client.audio.stream.StreamEmitter;
import com.ravecraft.mod.network.payload.StreamClosePayload;
import com.ravecraft.mod.network.payload.StreamOpenPayload;
import com.ravecraft.mod.network.payload.StreamOrderPayload;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.BlockPos;

/**
 * The tracks this player streams into each speaker. For every track it asks the server for a session, streams the
 * file once the server grants it, then moves on. Everyone in range, this player included, hears the result through
 * {@link ReceivedStreams}. Client thread only.
 */
public final class LocalPlayback {
	private static final Map<BlockPos, Session> SESSIONS = new HashMap<>();
	/** Numbers this client's open requests, so a late answer to an abandoned one is recognised. */
	private static int lastRequest;

	private LocalPlayback() {
	}

	public static void register() {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> stopAll());
	}

	/** Replaces whatever this player was streaming into the speaker at {@code pos} with {@code tracks}, in order. */
	public static void play(BlockPos pos, List<Path> tracks) {
		stop(pos);
		if (tracks.isEmpty() || !ClientPlayNetworking.canSend(StreamOpenPayload.TYPE)) {
			return;
		}
		Session session = new Session(pos.immutable(), new ArrayDeque<>(tracks));
		SESSIONS.put(session.pos, session);
		session.next();
	}

	public static void stop(BlockPos pos) {
		Session session = SESSIONS.remove(pos);
		if (session != null) {
			session.close(false);
		}
	}

	/** Leaving the world: the server already ends everything, so nothing is sent. */
	public static void stopAll() {
		SESSIONS.values().forEach(Session::cancel);
		SESSIONS.clear();
	}

	public static Optional<Path> nowPlaying(BlockPos pos) {
		Session session = SESSIONS.get(pos);
		return session == null ? Optional.empty() : Optional.ofNullable(session.current);
	}

	/** The server granted (start), refused or revoked (stop) a session. */
	public static void onOrder(StreamOrderPayload order) {
		Session session = SESSIONS.get(order.pos());
		if (order.start()) {
			if (session == null || !session.awaitingGrant || session.request != order.request()) {
				// Answer to an abandoned request: give the session back.
				ClientPlayNetworking.send(new StreamClosePayload(order.sessionId(), false));
				return;
			}
			session.begin(order.sessionId());
			return;
		}
		if (session == null) {
			return;
		}
		boolean refused = session.awaitingGrant && order.request() == session.request;
		boolean revoked = order.sessionId() != StreamOrderPayload.NO_SESSION
				&& (order.sessionId() == session.sessionId || order.sessionId() == session.previousId);
		if (refused || revoked) {
			SESSIONS.remove(session.pos);
			session.cancel();
			if (revoked) {
				ReceivedStreams.end(order.sessionId(), false);
			}
		}
	}

	private static final class Session {
		final BlockPos pos;
		final Deque<Path> queue;
		Path current;
		boolean awaitingGrant;
		int request;
		int sessionId = -1;
		/** The session the pending request renews: the server ends it, with its tail, when granting the next. */
		int previousId = -1;
		StreamEmitter emitter;

		Session(BlockPos pos, Deque<Path> queue) {
			this.pos = pos;
			this.queue = queue;
		}

		/** Asks for a session for the next queued track; forgets this speaker when the queue is empty. */
		void next() {
			sessionId = -1;
			emitter = null;
			current = queue.poll();
			if (current == null) {
				SESSIONS.remove(pos, this);
				return;
			}
			awaitingGrant = true;
			request = ++lastRequest;
			ClientPlayNetworking.send(new StreamOpenPayload(pos, request));
		}

		void begin(int id) {
			awaitingGrant = false;
			previousId = -1;
			sessionId = id;
			emitter = new StreamEmitter(id, pos, current, () -> ended(id, true), e -> ended(id, false));
			emitter.start();
		}

		/**
		 * The track played out ({@code finished}) or could not be read. The next track renews the session, which
		 * the server allows from out of reach; after the last one the session is closed.
		 */
		void ended(int id, boolean finished) {
			if (SESSIONS.get(pos) != this || sessionId != id) {
				return;
			}
			ReceivedStreams.end(id, finished);
			if (queue.isEmpty()) {
				ClientPlayNetworking.send(new StreamClosePayload(id, finished));
				SESSIONS.remove(pos, this);
				return;
			}
			previousId = id;
			next();
		}

		void close(boolean finished) {
			cancel();
			if (sessionId >= 0) {
				ClientPlayNetworking.send(new StreamClosePayload(sessionId, finished));
				ReceivedStreams.end(sessionId, finished);
			}
		}

		void cancel() {
			if (emitter != null) {
				emitter.cancel();
			}
		}
	}
}
