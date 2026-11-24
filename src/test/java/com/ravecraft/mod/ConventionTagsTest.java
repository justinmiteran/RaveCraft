package com.ravecraft.mod;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Recipes and tags follow the Fabric conventions, so the materials of other mods work in RaveCraft and back. */
class ConventionTagsTest {
	private static final Path DATA = Path.of("src/main/generated/data");
	private static final Path LANG = Path.of("src/main/resources/assets/ravecraft/lang");
	private static final String FABRIC_LANG = "assets/fabric-convention-tags-v2/lang/en_us.json";
	/** Items that have a convention tag: a recipe must ask for the tag, never the item. */
	private static final Map<String, String> TAGGED = Map.ofEntries(
			Map.entry("minecraft:iron_ingot", "c:ingots/iron"),
			Map.entry("minecraft:copper_ingot", "c:ingots/copper"),
			Map.entry("minecraft:gold_nugget", "c:nuggets/gold"),
			Map.entry("minecraft:redstone", "c:dusts/redstone"),
			Map.entry("minecraft:redstone_block", "c:storage_blocks/redstone"),
			Map.entry("minecraft:iron_block", "c:storage_blocks/iron"),
			Map.entry("minecraft:quartz", "c:gems/quartz"),
			Map.entry("minecraft:amethyst_shard", "c:gems/amethyst"),
			Map.entry("minecraft:glass", "c:glass_blocks/colorless"),
			Map.entry("ravecraft:raw_neodymium", "c:raw_materials/neodymium"),
			Map.entry("ravecraft:neodymium_ingot", "c:ingots/neodymium"),
			Map.entry("ravecraft:silicon", "c:silicon"),
			Map.entry("ravecraft:deepslate_neodymium_ore", "c:ores/neodymium"),
			Map.entry("ravecraft:raw_neodymium_block", "c:storage_blocks/raw_neodymium"),
			Map.entry("ravecraft:neodymium_block", "c:storage_blocks/neodymium"),
			Map.entry("ravecraft:silicon_block", "c:storage_blocks/silicon"));
	/** Recipe fields that name what is made or how, not what is used. */
	private static final Set<String> NOT_INGREDIENTS = Set.of("result", "type", "category", "group");
	/** Convention tag folders that are groups only, without a parent tag. */
	private static final Set<String> NO_PARENT = Set.of("ores_in_ground");

	@Test
	void recipesAskForConventionTags() throws IOException {
		List<String> found = new ArrayList<>();
		for (Path recipe : jsonFiles(DATA.resolve("ravecraft/recipe"))) {
			collectTagged(read(recipe), recipe.getFileName().toString(), null, found);
		}
		assertTrue(found.isEmpty(), "use the convention tag instead: " + found);
	}

	@Test
	void conventionSubtagsAreInTheirParent() throws IOException {
		for (String registry : List.of("item", "block")) {
			Path tags = DATA.resolve("c/tags/" + registry);
			for (Path tag : jsonFiles(tags)) {
				Path relative = tags.relativize(tag);
				if (relative.getNameCount() < 2 || NO_PARENT.contains(relative.getName(0).toString())) {
					continue;
				}
				String id = "#c:" + name(relative);
				Path parent = tags.resolve(relative.getName(0) + ".json");
				assertTrue(Files.isRegularFile(parent) && read(parent).getAsJsonArray("values").toString()
						.contains("\"" + id + "\""), registry + " tag c:" + relative.getName(0) + " lacks " + id);
			}
		}
	}

	@Test
	void itemTagsAreTranslated() throws IOException {
		JsonObject fabric = fabricLang();
		List<String> keys = new ArrayList<>();
		for (String namespace : List.of("c", "ravecraft")) {
			Path tags = DATA.resolve(namespace + "/tags/item");
			jsonFiles(tags).forEach(tag -> keys.add(
					"tag.item." + namespace + "." + name(tags.relativize(tag)).replace('/', '.')));
		}
		for (String file : List.of("en_us.json", "fr_fr.json")) {
			JsonObject lang = read(LANG.resolve(file));
			keys.forEach(key -> assertTrue(lang.has(key) || fabric.has(key), file + " is missing " + key));
		}
	}

	/** Adds, as {@code file: item}, every ingredient naming an item that has a convention tag. */
	private static void collectTagged(JsonElement json, String file, String field, List<String> found) {
		if (json.isJsonObject()) {
			json.getAsJsonObject().entrySet().stream()
					.filter(entry -> !NOT_INGREDIENTS.contains(entry.getKey()))
					.forEach(entry -> collectTagged(entry.getValue(), file, entry.getKey(), found));
		} else if (json.isJsonArray()) {
			json.getAsJsonArray().forEach(element -> collectTagged(element, file, field, found));
		} else if (json.isJsonPrimitive() && TAGGED.containsKey(json.getAsString())) {
			found.add(file + ": " + json.getAsString() + " -> #" + TAGGED.get(json.getAsString()));
		}
	}

	private static String name(Path relative) {
		String path = relative.toString().replace('\\', '/');
		return path.substring(0, path.length() - ".json".length());
	}

	private static List<Path> jsonFiles(Path folder) throws IOException {
		if (!Files.isDirectory(folder)) {
			return List.of();
		}
		try (Stream<Path> files = Files.walk(folder)) {
			return files.filter(path -> path.toString().endsWith(".json")).sorted().toList();
		}
	}

	private static JsonObject read(Path path) throws IOException {
		return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
	}

	private static JsonObject fabricLang() throws IOException {
		InputStream stream = ConventionTagsTest.class.getClassLoader().getResourceAsStream(FABRIC_LANG);
		assertNotNull(stream, "Fabric convention tag translations not on the test classpath");
		try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		}
	}
}
