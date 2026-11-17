package com.ravecraft.mod.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.ravecraft.mod.core.acoustics.AcousticSettings;
import com.ravecraft.mod.core.acoustics.DeviceProfile;
import java.io.Reader;
import java.io.Writer;
import java.util.function.Consumer;

/** Reads and writes {@code ravecraft-server.json}. A missing field takes its default; values are clamped. */
public final class AcousticSettingsJson {
	private AcousticSettingsJson() {
	}

	public static AcousticSettings read(Reader in, Consumer<String> warn) {
		AcousticSettings d = AcousticSettings.DEFAULT;
		try {
			JsonElement root = JsonParser.parseReader(in);
			if (!root.isJsonObject()) {
				throw new JsonParseException("not an object");
			}
			JsonObject o = root.getAsJsonObject();
			JsonObject p = object(object(o, "profiles"), "portable");
			DeviceProfile dp = d.portable();
			AcousticSettings read = new AcousticSettings(
					number(o, "rolloff", d.rolloff()),
					number(o, "fadeFraction", d.fadeFraction()),
					number(o, "airAbsorptionDbPerBlock", d.airAbsorptionDbPerBlock()),
					new DeviceProfile(number(p, "range", dp.range()), number(p, "bassCutDb", dp.bassCutDb()),
							number(p, "bassCutHz", dp.bassCutHz()), dp.directivity()));
			return read.clamped(warn);
		} catch (RuntimeException e) {
			warn.accept("unreadable (" + e.getMessage() + "), using defaults");
			return d;
		}
	}

	public static void write(AcousticSettings s, Writer out) {
		JsonObject portable = new JsonObject();
		portable.addProperty("range", s.portable().range());
		portable.addProperty("bassCutDb", s.portable().bassCutDb());
		portable.addProperty("bassCutHz", s.portable().bassCutHz());
		JsonObject profiles = new JsonObject();
		profiles.add("portable", portable);
		JsonObject root = new JsonObject();
		root.addProperty("rolloff", s.rolloff());
		root.addProperty("fadeFraction", s.fadeFraction());
		root.addProperty("airAbsorptionDbPerBlock", s.airAbsorptionDbPerBlock());
		root.add("profiles", profiles);
		new GsonBuilder().setPrettyPrinting().create().toJson(root, out);
	}

	private static JsonObject object(JsonObject parent, String key) {
		return parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : new JsonObject();
	}

	private static double number(JsonObject parent, String key, double fallback) {
		return parent.has(key) ? parent.get(key).getAsDouble() : fallback;
	}
}
