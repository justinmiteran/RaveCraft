package com.ravecraft.mod.client.audio.stream;

import com.ravecraft.mod.client.audio.ClientSpeakerHosts;
import com.ravecraft.mod.client.audio.LocalPlayback;
import com.ravecraft.mod.client.options.RaveCraftOptions;
import com.ravecraft.mod.client.screen.SpeakerStates;
import com.ravecraft.mod.network.payload.NowPlayingPayload;
import com.ravecraft.mod.network.payload.RelayedChunkPayload;
import com.ravecraft.mod.network.payload.SessionEndPayload;
import com.ravecraft.mod.network.payload.SpeakerStatePayload;
import com.ravecraft.mod.network.payload.StreamOrderPayload;
import com.ravecraft.mod.network.payload.TrackRemovedPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/** Client receivers of the audio relay. Fabric runs them on the client thread. */
public final class ClientRelay {
	private ClientRelay() {
	}

	/** Registers the receivers of the relayed audio, of the session ends and of what plays. */
	public static void init() {
		ReceivedStreams.register();
		NowPlayingToasts.register();
		SpeakerStates.register();
		ClientSpeakerHosts.register();
		ClientPlayNetworking.registerGlobalReceiver(RelayedChunkPayload.TYPE,
				(payload, context) -> ReceivedStreams.offer(payload.sessionId(), payload.speakerId(), payload.seq(), payload.frames()));
		ClientPlayNetworking.registerGlobalReceiver(SessionEndPayload.TYPE,
				(payload, context) -> {
					ReceivedStreams.end(payload.sessionId(), payload.drain());
					NowPlayingToasts.onSessionEnd(payload.sessionId());
				});
		ClientPlayNetworking.registerGlobalReceiver(NowPlayingPayload.TYPE, (payload, context) -> NowPlayingToasts.onNotice(payload));
		ClientPlayNetworking.registerGlobalReceiver(StreamOrderPayload.TYPE,
				(payload, context) -> LocalPlayback.onOrder(payload));
		ClientPlayNetworking.registerGlobalReceiver(SpeakerStatePayload.TYPE, (payload, context) -> SpeakerStates.put(payload));
		ClientPlayNetworking.registerGlobalReceiver(TrackRemovedPayload.TYPE, (payload, context) -> {
			if (RaveCraftOptions.notifications().get().errors()) {
				SystemToast.add(Minecraft.getInstance().gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
						Component.translatable("toast.ravecraft.track_removed.title"),
						Component.translatable("toast.ravecraft.track_removed.message", payload.title()));
			}
		});
	}
}
