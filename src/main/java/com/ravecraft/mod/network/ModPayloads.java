package com.ravecraft.mod.network;

import com.ravecraft.mod.network.payload.AudioChunkPayload;
import com.ravecraft.mod.network.payload.QueueAddPayload;
import com.ravecraft.mod.network.payload.RelayedChunkPayload;
import com.ravecraft.mod.network.payload.SessionEndPayload;
import com.ravecraft.mod.network.payload.SpeakerCommandPayload;
import com.ravecraft.mod.network.payload.SpeakerStatePayload;
import com.ravecraft.mod.network.payload.SpeakerWatchPayload;
import com.ravecraft.mod.network.payload.StreamClosePayload;
import com.ravecraft.mod.network.payload.StreamOrderPayload;
import com.ravecraft.mod.network.payload.StreamResultPayload;
import com.ravecraft.mod.network.payload.TrackRemovedPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Registers the mod's play payloads on both sides. */
public final class ModPayloads {
	private ModPayloads() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(QueueAddPayload.TYPE, QueueAddPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SpeakerCommandPayload.TYPE, SpeakerCommandPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SpeakerWatchPayload.TYPE, SpeakerWatchPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(StreamResultPayload.TYPE, StreamResultPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(AudioChunkPayload.TYPE, AudioChunkPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(StreamClosePayload.TYPE, StreamClosePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(StreamOrderPayload.TYPE, StreamOrderPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(RelayedChunkPayload.TYPE, RelayedChunkPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SessionEndPayload.TYPE, SessionEndPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SpeakerStatePayload.TYPE, SpeakerStatePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(TrackRemovedPayload.TYPE, TrackRemovedPayload.CODEC);
	}
}
