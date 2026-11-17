package com.ravecraft.mod.client.audio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.core.queue.TrackTable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;

/**
 * This player's {@link TrackTable}, kept in {@code config/ravecraft-tracks.json} so queued tracks survive a restart.
 * Loaded on first use; a missing or corrupt file starts an empty table. Client thread only.
 */
public final class TrackTableStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static TrackTable table;

	private TrackTableStore() {
	}

	public static void register(Map<UUID, Path> added) {
		TrackTable loaded = table();
		added.forEach((id, path) -> loaded.put(id, path.toAbsolutePath().toString()));
		save(loaded);
	}

	public static Optional<Path> resolve(UUID entryId) {
		return table().get(entryId).flatMap(TrackTableStore::toPath);
	}

	private static Optional<Path> toPath(String path) {
		try {
			return Optional.of(Path.of(path));
		} catch (InvalidPathException e) {
			return Optional.empty();
		}
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("ravecraft-tracks.json");
	}

	private static TrackTable table() {
		if (table == null) {
			table = load(file());
		}
		return table;
	}

	private static TrackTable load(Path file) {
		if (!Files.isRegularFile(file)) {
			return new TrackTable();
		}
		try {
			Map<UUID, String> ordered = new LinkedHashMap<>();
			for (JsonElement element : JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonArray()) {
				JsonObject entry = element.getAsJsonObject();
				ordered.put(UUID.fromString(entry.get("id").getAsString()), entry.get("path").getAsString());
			}
			return TrackTable.of(ordered);
		} catch (IOException | RuntimeException e) {
			RaveCraft.LOGGER.warn("Cannot read {}, starting an empty track table: {}", file, e.getMessage());
			return new TrackTable();
		}
	}

	private static void save(TrackTable table) {
		JsonArray array = new JsonArray();
		table.snapshot().forEach((id, path) -> {
			JsonObject entry = new JsonObject();
			entry.addProperty("id", id.toString());
			entry.addProperty("path", path);
			array.add(entry);
		});
		Path file = file();
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(array), StandardCharsets.UTF_8);
		} catch (IOException e) {
			RaveCraft.LOGGER.warn("Cannot write {}: {}", file, e.getMessage());
		}
	}
}
