package com.ravecraft.mod.client.audio;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;

/**
 * What each speaker is playing for the local player: a queue of tracks and the sound of the current one. Nothing is
 * shared with the server or other players yet (that is the relay, step 3). All methods run on the client thread.
 */
public final class LocalPlayback {
	private static final Map<BlockPos, Session> SESSIONS = new HashMap<>();

	private LocalPlayback() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(LocalPlayback::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> stopAll());
	}

	/** Replaces whatever the speaker at {@code pos} was playing with {@code tracks}, in order. */
	public static void play(BlockPos pos, List<Path> tracks) {
		stop(pos);
		if (tracks.isEmpty()) {
			return;
		}
		Session session = new Session(pos.immutable(), new ArrayDeque<>(tracks));
		SESSIONS.put(session.pos, session);
		session.next();
	}

	public static void stop(BlockPos pos) {
		Session session = SESSIONS.remove(pos);
		if (session != null) {
			session.halt();
		}
	}

	public static void stopAll() {
		SESSIONS.values().forEach(Session::halt);
		SESSIONS.clear();
	}

	public static Optional<Path> nowPlaying(BlockPos pos) {
		Session session = SESSIONS.get(pos);
		return session == null || session.current == null ? Optional.empty() : Optional.of(session.current.track());
	}

	/** Moves each finished session to its next track; at most one step per speaker per tick. */
	private static void tick(Minecraft client) {
		if (SESSIONS.isEmpty()) {
			return;
		}
		SoundManager sounds = client.getSoundManager();
		Iterator<Session> it = SESSIONS.values().iterator();
		while (it.hasNext()) {
			Session session = it.next();
			if (session.current != null && sounds.isActive(session.current)) {
				continue;
			}
			if ((session.current != null && session.current.speakerGone()) || !session.next()) {
				it.remove();
			}
		}
	}

	private static final class Session {
		final BlockPos pos;
		final Deque<Path> queue;
		SpeakerSoundInstance current;

		Session(BlockPos pos, Deque<Path> queue) {
			this.pos = pos;
			this.queue = queue;
		}

		/** Starts the next queued track; false when the queue is empty. */
		boolean next() {
			Path track = queue.poll();
			if (track == null) {
				current = null;
				return false;
			}
			current = new SpeakerSoundInstance(pos, track);
			Minecraft.getInstance().getSoundManager().play(current);
			return true;
		}

		void halt() {
			if (current != null) {
				current.halt();
				Minecraft.getInstance().getSoundManager().stop(current);
			}
		}
	}
}
