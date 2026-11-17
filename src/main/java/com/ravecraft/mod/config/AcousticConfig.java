package com.ravecraft.mod.config;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.core.acoustics.AcousticSettings;
import com.ravecraft.mod.network.Net;
import com.ravecraft.mod.network.payload.AcousticSettingsPayload;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The server's acoustic model, loaded from {@code config/ravecraft-server.json} when the server starts (written
 * with the defaults if missing) and sent to every player who joins.
 */
public final class AcousticConfig {
	public static final String FILE_NAME = "ravecraft-server.json";
	private static volatile AcousticSettings settings = AcousticSettings.DEFAULT;

	private AcousticConfig() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> load(FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME)));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				Net.send(handler.player, new AcousticSettingsPayload(settings)));
	}

	public static AcousticSettings get() {
		return settings;
	}

	/** Replaces the settings in memory (tests); players already connected keep the old ones. */
	public static void set(AcousticSettings value) {
		settings = value;
	}

	static void load(Path file) {
		if (Files.notExists(file)) {
			settings = AcousticSettings.DEFAULT;
			try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				AcousticSettingsJson.write(settings, out);
			} catch (IOException e) {
				RaveCraft.LOGGER.warn("{}: cannot write defaults: {}", FILE_NAME, e.toString());
			}
			return;
		}
		try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			settings = AcousticSettingsJson.read(in, warning -> RaveCraft.LOGGER.warn("{}: {}", FILE_NAME, warning));
		} catch (IOException e) {
			RaveCraft.LOGGER.warn("{}: unreadable ({}), using defaults", FILE_NAME, e.toString());
			settings = AcousticSettings.DEFAULT;
		}
	}
}
