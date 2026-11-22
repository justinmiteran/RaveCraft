package com.ravecraft.mod.client.screen;

import com.ravecraft.mod.network.payload.SpeakerCommandPayload;
import com.ravecraft.mod.network.payload.SpeakerCommandPayload.Command;
import com.ravecraft.mod.network.payload.SpeakerStatePayload;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.Util;

/**
 * The latest queue state received for each speaker whose screen this player opened. Client thread only; emptied
 * when leaving the world.
 */
public final class SpeakerStates {
	/** A state and when it arrived. */
	private record Received(SpeakerStatePayload state, long atMs) {
	}

	private static final Map<UUID, Received> STATES = new HashMap<>();

	private SpeakerStates() {
	}

	/** Forgets every state on leaving. */
	public static void register() {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> STATES.clear());
	}

	/**
	 * Keeps {@code state}, the latest of its speaker.
	 *
	 * @param state the speaker's new state
	 */
	public static void put(SpeakerStatePayload state) {
		STATES.put(state.speakerId(), new Received(state, Util.getMillis()));
	}

	/**
	 * The latest state of speaker {@code speakerId}, if any.
	 *
	 * @param speakerId the speaker
	 * @return the latest state, or empty
	 */
	public static Optional<SpeakerStatePayload> get(UUID speakerId) {
		return Optional.ofNullable(STATES.get(speakerId)).map(Received::state);
	}

	/**
	 * Where the current track is now, estimated from the last state: the server only sends changes.
	 *
	 * @param speakerId the speaker
	 * @return the estimated position in the track, in milliseconds
	 */
	public static long positionMs(UUID speakerId) {
		Received received = STATES.get(speakerId);
		if (received == null) {
			return 0;
		}
		long elapsed = received.state().playing() ? Util.getMillis() - received.atMs() : 0;
		long position = received.state().positionMs() + Math.max(0, elapsed);
		long duration = received.state().current()
				.flatMap(id -> received.state().entries().stream().filter(e -> e.entryId().equals(id)).findFirst())
				.map(SpeakerStatePayload.StateEntry::durationMs).orElse(-1L);
		return clamp(position, duration);
	}

	/** {@code position} kept within {@code [0, durationMs]} when the duration is known (>= 0), unchanged otherwise. */
	static long clamp(long position, long durationMs) {
		return durationMs >= 0 ? Math.max(0, Math.min(position, durationMs)) : position;
	}

	/**
	 * Sends a command with a numeric value (seek position, volume...) when the server accepts speaker commands.
	 *
	 * @param speakerId the speaker
	 * @param command the command
	 * @param value the command's value
	 */
	public static void send(UUID speakerId, Command command, long value) {
		send(speakerId, command, Optional.empty(), value);
	}

	/**
	 * Sends a command about one entry (or none) when the server accepts speaker commands.
	 *
	 * @param speakerId the speaker
	 * @param command the command
	 * @param entryId the entry it is about, or empty
	 */
	public static void send(UUID speakerId, Command command, Optional<UUID> entryId) {
		send(speakerId, command, entryId, 0);
	}

	/**
	 * Sends a command about one entry with a numeric value (MOVE's index) when the server accepts speaker commands.
	 *
	 * @param speakerId the speaker
	 * @param command the command
	 * @param entryId the entry it is about, or empty
	 * @param value the command's value
	 */
	public static void send(UUID speakerId, Command command, Optional<UUID> entryId, long value) {
		if (ClientPlayNetworking.canSend(SpeakerCommandPayload.TYPE)) {
			ClientPlayNetworking.send(new SpeakerCommandPayload(speakerId, command, entryId, value));
		}
	}

	/**
	 * Forgets the state of speaker {@code speakerId}.
	 *
	 * @param speakerId the speaker
	 */
	public static void forget(UUID speakerId) {
		STATES.remove(speakerId);
	}
}
