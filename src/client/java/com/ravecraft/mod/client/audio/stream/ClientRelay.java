package com.ravecraft.mod.client.audio.stream;

import com.ravecraft.mod.client.audio.LocalPlayback;
import com.ravecraft.mod.network.payload.RelayedChunkPayload;
import com.ravecraft.mod.network.payload.SessionEndPayload;
import com.ravecraft.mod.network.payload.StreamOrderPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Client receivers of the audio relay. Fabric runs them on the client thread. */
public final class ClientRelay {
	private ClientRelay() {
	}

	public static void init() {
		ReceivedStreams.register();
		ClientPlayNetworking.registerGlobalReceiver(RelayedChunkPayload.TYPE,
				(payload, context) -> ReceivedStreams.offer(payload.sessionId(), payload.pos(), payload.seq(), payload.frames()));
		ClientPlayNetworking.registerGlobalReceiver(SessionEndPayload.TYPE,
				(payload, context) -> ReceivedStreams.end(payload.sessionId(), payload.drain()));
		ClientPlayNetworking.registerGlobalReceiver(StreamOrderPayload.TYPE,
				(payload, context) -> LocalPlayback.onOrder(payload));
	}
}
