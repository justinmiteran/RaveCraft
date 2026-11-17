package com.ravecraft.mod.client.screen;

import com.ravecraft.mod.network.payload.SpeakerStatePayload;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
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
		return received.state().positionMs() + Math.max(0, elapsed);
	}

	public static void forget(BlockPos pos) {
		STATES.remove(pos);
	}
}
