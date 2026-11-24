package com.ravecraft.mod.client.screen.machine;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.core.machine.ProgressColors;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Reads {@code assets/ravecraft/machine_colors.json} from every resource pack, lower packs first, so a pack can recolour
 * an entry or add items from other mods without repeating the others. A broken file is logged and ignored.
 */
final class MachineColorsLoader {
	private static final Identifier FILE = RaveCraft.id("machine_colors.json");

	private MachineColorsLoader() {
	}

	/**
	 * Loads the colour table.
	 *
	 * @param resources the client resource manager
	 * @return the merged table, empty when no pack has the file
	 */
	static ProgressColors load(ResourceManager resources) {
		Map<String, String> entries = new HashMap<>();
		for (Resource resource : resources.getResourceStack(FILE)) {
			try (Reader reader = resource.openAsReader()) {
				readInto(JsonParser.parseReader(reader).getAsJsonObject(), entries);
			} catch (IOException | RuntimeException e) {
				RaveCraft.LOGGER.warn("Ignoring broken {} in pack {}", FILE, resource.sourcePackId(), e);
			}
		}
		ProgressColors colors = ProgressColors.parse(entries);
		if (colors.skipped() > 0) {
			RaveCraft.LOGGER.warn("Skipped {} malformed colours in {}", colors.skipped(), FILE);
		}
		return colors;
	}

	private static void readInto(JsonObject json, Map<String, String> entries) {
		for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
			entries.put(entry.getKey(), entry.getValue().getAsString());
		}
	}
}
