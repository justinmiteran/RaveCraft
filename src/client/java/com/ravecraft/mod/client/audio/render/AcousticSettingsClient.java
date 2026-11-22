package com.ravecraft.mod.client.audio.render;

import com.ravecraft.mod.core.acoustics.AcousticSettings;
import com.ravecraft.mod.network.payload.AcousticSettingsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** The acoustic model received from the server; defaults until it arrives and after leaving. Any thread. */
public final class AcousticSettingsClient {
	private static volatile AcousticSettings settings = AcousticSettings.DEFAULT;

	private AcousticSettingsClient() {
	}

	/** Takes the settings the server sends, and the defaults again on leaving. */
	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(AcousticSettingsPayload.TYPE, (payload, context) -> settings = payload.settings());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> settings = AcousticSettings.DEFAULT);
	}

	/**
	 * The settings in force.
	 *
	 * @return the current acoustic settings
	 */
	public static AcousticSettings get() {
		return settings;
	}
}
