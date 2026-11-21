package com.ravecraft.mod.config;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.core.acoustics.AcousticSettings;
import com.ravecraft.mod.core.io.AtomicFiles;
import com.ravecraft.mod.network.Net;
import com.ravecraft.mod.network.payload.AcousticSettingsPayload;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The server's acoustic model, loaded from {@code config/ravecraft-server.json5} when the server starts (written
 * with the defaults and a comment per setting if missing) and sent to every player who joins.
 */
public final class AcousticConfig {
	public static final String FILE_NAME = "ravecraft-server.json5";
	private static volatile AcousticSettings settings = AcousticSettings.DEFAULT;

	private AcousticConfig() {
	}

	/** Loads the file when the server starts and sends the settings to each player who joins. */
	public static void init() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> load(FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME)));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				Net.send(handler.player, new AcousticSettingsPayload(settings)));
	}

	/** The settings in force on the server. */
	public static AcousticSettings get() {
		return settings;
	}

	/** Replaces the settings in memory (tests); players already connected keep the old ones. */
	public static void set(AcousticSettings value) {
		settings = value;
	}

	/** Reads {@code file}, writing the defaults when it is missing and completing it when it lacks keys. */
	static void load(Path file) {
		if (Files.notExists(file)) {
			settings = AcousticSettings.DEFAULT;
			try {
				AtomicFiles.write(file, out -> AcousticSettingsJson.write(settings, out));
			} catch (IOException e) {
				RaveCraft.LOGGER.warn("{}: cannot write defaults: {}", FILE_NAME, e.toString());
			}
			return;
		}
		boolean complete;
		try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			AcousticSettingsJson.Result result =
					AcousticSettingsJson.read(in, warning -> RaveCraft.LOGGER.warn("{}: {}", FILE_NAME, warning));
			settings = result.settings();
			complete = result.complete();
		} catch (IOException e) {
			RaveCraft.LOGGER.warn("{}: unreadable ({}), using the defaults until the file is fixed", FILE_NAME, e.toString());
			settings = AcousticSettings.DEFAULT;
			return;
		}
		// Rewrite only once the file is closed: Windows refuses to replace a file that is still open.
		if (!complete) {
			rewrite(file);
		}
	}

	/**
	 * Brings an incomplete file up to date: the old text is kept as {@code <name>.bak}, then the new text replaces
	 * the file as a whole, so a failure never leaves a half-written config.
	 */
	private static void rewrite(Path file) {
		Path backup = file.resolveSibling(file.getFileName() + ".bak");
		try {
			Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
			AtomicFiles.write(file, out -> AcousticSettingsJson.write(settings, out));
			RaveCraft.LOGGER.info("{}: added the missing settings, the previous file is kept as {}", FILE_NAME,
					backup.getFileName());
		} catch (IOException | RuntimeException e) {
			RaveCraft.LOGGER.warn("{}: cannot update the file, leaving it as it is: {}", FILE_NAME, e.toString());
		}
	}
}
