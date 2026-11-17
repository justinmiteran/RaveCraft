package com.ravecraft.mod.network;

import com.ravecraft.mod.network.payload.AudioChunkPayload;
import com.ravecraft.mod.network.payload.RelayedChunkPayload;
import com.ravecraft.mod.network.payload.SessionEndPayload;
import com.ravecraft.mod.network.payload.StreamClosePayload;
import com.ravecraft.mod.network.payload.StreamOpenPayload;
import com.ravecraft.mod.network.payload.StreamOrderPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Registers the mod's play payloads on both sides. */
public final class ModPayloads {
	private ModPayloads() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(StreamOpenPayload.TYPE, StreamOpenPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(AudioChunkPayload.TYPE, AudioChunkPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(StreamClosePayload.TYPE, StreamClosePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(StreamOrderPayload.TYPE, StreamOrderPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(RelayedChunkPayload.TYPE, RelayedChunkPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SessionEndPayload.TYPE, SessionEndPayload.CODEC);
	}
}
