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
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;

/**
 * The latest queue state received for each speaker whose screen this player opened. Client thread only; emptied
 * when leaving the world.
 */
public final class SpeakerStates {
	private record Received(SpeakerStatePayload state, long atMs) {
	}

	private static final Map<BlockPos, Received> STATES = new HashMap<>();

	private SpeakerStates() {
	}

	public static void register() {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> STATES.clear());
	}

	public static void put(SpeakerStatePayload state) {
		STATES.put(state.pos().immutable(), new Received(state, Util.getMillis()));
	}

	public static Optional<SpeakerStatePayload> get(BlockPos pos) {
		return Optional.ofNullable(STATES.get(pos)).map(Received::state);
	}

	/** Where the current track is now, estimated from the last state: the server only sends changes. */
	public static long positionMs(BlockPos pos) {
		Received received = STATES.get(pos);
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

	/** Sends a command with a numeric value (seek position, volume...) when the server accepts speaker commands. */
	public static void send(BlockPos pos, Command command, long value) {
		if (ClientPlayNetworking.canSend(SpeakerCommandPayload.TYPE)) {
			ClientPlayNetworking.send(new SpeakerCommandPayload(pos, command, Optional.empty(), value));
		}
	}

	/** Sends a command about one entry (or none) when the server accepts speaker commands. */
	public static void send(BlockPos pos, Command command, Optional<UUID> entryId) {
		if (ClientPlayNetworking.canSend(SpeakerCommandPayload.TYPE)) {
			ClientPlayNetworking.send(new SpeakerCommandPayload(pos, command, entryId));
		}
	}

	public static void forget(BlockPos pos) {
		STATES.remove(pos);
	}
}
