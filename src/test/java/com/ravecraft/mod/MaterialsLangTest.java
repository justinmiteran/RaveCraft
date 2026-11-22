package com.ravecraft.mod;

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

class MaterialsLangTest {
	private static final Path LANG = Path.of("src/main/resources/assets/ravecraft/lang");
	private static final List<String> BLOCKS = List.of("oil_shale", "deepslate_neodymium_ore", "shale", "shale_stairs",
			"shale_slab", "shale_wall", "polished_shale", "polished_shale_stairs", "polished_shale_slab",
			"polished_shale_wall", "shale_bricks", "shale_brick_stairs", "shale_brick_slab", "shale_brick_wall",
			"chiseled_shale");
	private static final List<String> ITEMS = List.of("bitumen", "raw_neodymium", "neodymium_ingot", "silicon",
			"silicon_wafer");

	@Test
	void everyMaterialHasBothTranslations() throws IOException {
		for (String file : List.of("en_us.json", "fr_fr.json")) {
			JsonObject lang = JsonParser.parseString(Files.readString(LANG.resolve(file), StandardCharsets.UTF_8))
					.getAsJsonObject();
			BLOCKS.forEach(id -> assertTranslated(lang, file, "block.ravecraft." + id));
			ITEMS.forEach(id -> assertTranslated(lang, file, "item.ravecraft." + id));
		}
	}

	private static void assertTranslated(JsonObject lang, String file, String key) {
		assertTrue(lang.has(key), file + " is missing " + key);
		assertFalse(lang.get(key).getAsString().isBlank(), file + " has a blank " + key);
	}
}
