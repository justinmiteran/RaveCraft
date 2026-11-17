package com.ravecraft.mod.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Sends a payload only to players whose client has the mod (and a connection: GameTest mock players have none). */
public final class Net {
	private Net() {
	}

	public static void send(ServerPlayer player, CustomPacketPayload payload) {
		if (player.connection != null && ServerPlayNetworking.canSend(player, payload.type())) {
			ServerPlayNetworking.send(player, payload);
		}
	}
}
