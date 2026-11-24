package com.ravecraft.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class MachineSoundsTest {
	private static final Path ASSETS = Path.of("src/main/resources/assets/ravecraft");
	private static final List<String> MACHINES = List.of("coil_winder", "magnetizer", "extruder", "etcher");

	@Test
	void everyMachineLoopIsDeclaredWithItsClip() throws IOException {
		JsonObject sounds = read(ASSETS.resolve("sounds.json"));
		for (String machine : MACHINES) {
			JsonObject event = sounds.getAsJsonObject("machine." + machine);
			assertTrue(event != null, "sounds.json is missing machine." + machine);
			JsonObject clip = event.getAsJsonArray("sounds").get(0).getAsJsonObject();
			assertEquals("ravecraft:machine/" + machine, clip.get("name").getAsString());
			assertTrue(Files.isRegularFile(ASSETS.resolve("sounds/machine/" + machine + ".ogg")), machine + ".ogg");
			assertEquals("subtitles.ravecraft.machine." + machine, event.get("subtitle").getAsString());
		}
	}

	@Test
	void everyMachineSubtitleIsTranslated() throws IOException {
		for (String file : List.of("en_us.json", "fr_fr.json")) {
			JsonObject lang = read(ASSETS.resolve("lang/" + file));
			for (String machine : MACHINES) {
				String key = "subtitles.ravecraft.machine." + machine;
				assertTrue(lang.has(key), file + " is missing " + key);
				assertFalse(lang.get(key).getAsString().isBlank(), file + " has a blank " + key);
			}
		}
	}

	private static JsonObject read(Path path) throws IOException {
		return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
	}
}
