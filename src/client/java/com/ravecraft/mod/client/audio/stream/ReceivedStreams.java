package com.ravecraft.mod.client.audio.stream;

import com.ravecraft.mod.client.audio.SpeakerSoundInstance;
import com.ravecraft.mod.core.audio.stream.SessionRoster;
import com.ravecraft.mod.core.audio.stream.StreamPlayout;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;

/**
 * The streams this client is hearing, relayed or its own (loopback). A stream starts on its first chunk, with no
 * prior notice, so players walking into range just start hearing; one that went quiet is dropped and starts again
 * if chunks come back. Client thread only.
 */
public final class ReceivedStreams {
	private static final long IDLE_TIMEOUT_MS = 5_000;
	private static final int REMEMBERED_ENDED = 64;
	private static final SessionRoster<Stream> STREAMS = new SessionRoster<>(REMEMBERED_ENDED);
	/** Ended streams still playing their buffered tail. */
	private static final List<Stream> DRAINING = new ArrayList<>();

	private ReceivedStreams() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> endAll());
	}

	public static void offer(int sessionId, BlockPos pos, int seq, List<byte[]> frames) {
		long now = Util.getMillis();
		STREAMS.admit(sessionId, () -> new Stream(pos.immutable(), new StreamPlayout(now))).ifPresent(stream -> {
			stream.playout.offer(seq, frames, now);
			stream.ensurePlaying();
		});
	}

	/** Ends a session: after its buffered tail when {@code drain}, at once otherwise. */
	public static void end(int sessionId, boolean drain) {
		STREAMS.end(sessionId).ifPresent(stream -> {
			if (drain) {
				stream.playout.finish();
				DRAINING.add(stream);
			} else {
				stream.stopped = true;
			}
		});
	}

	public static void endAll() {
		STREAMS.clear().forEach(stream -> stream.stopped = true);
		DRAINING.forEach(stream -> stream.stopped = true);
		DRAINING.clear();
	}

	/** Drops streams that went silent (owner vanished without notice, or this player walked away). */
	private static void tick() {
		long now = Util.getMillis();
		STREAMS.expireIf(stream -> stream.playout.idleFor(IDLE_TIMEOUT_MS, now)).forEach(stream -> stream.stopped = true);
		DRAINING.removeIf(stream -> !stream.alive());
	}

	private static final class Stream {
		final BlockPos pos;
		final StreamPlayout playout;
		volatile boolean stopped;
		SpeakerSoundInstance sound;

		Stream(BlockPos pos, StreamPlayout playout) {
			this.pos = pos;
			this.playout = playout;
		}

		boolean alive() {
			return !stopped && !playout.isDrained();
		}

		/** (Re)starts the sound if it never started or the engine dropped it (reload, no free source). */
		void ensurePlaying() {
			Minecraft minecraft = Minecraft.getInstance();
			SoundManager sounds = minecraft.getSoundManager();
			if ((sound == null || !sounds.isActive(sound)) && SpeakerSoundInstance.isPoweredSpeaker(minecraft.level, pos)) {
				sound = new SpeakerSoundInstance(pos, () -> new NetworkAudioStream(playout), this::alive);
				sounds.play(sound);
			}
		}
	}
}
