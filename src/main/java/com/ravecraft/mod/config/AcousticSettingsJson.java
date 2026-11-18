package com.ravecraft.mod.config;

import com.google.gson.Strictness;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.ravecraft.mod.core.acoustics.AcousticSettings;
import com.ravecraft.mod.core.acoustics.DeviceProfile;
import com.ravecraft.mod.core.acoustics.occlusion.AcousticMaterial;
import com.ravecraft.mod.core.acoustics.occlusion.AcousticMedium;
import com.ravecraft.mod.core.acoustics.occlusion.OcclusionSettings;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Reads and writes {@code ravecraft-server.json5}: JSON that may hold line and block comments. A missing
 * field takes its default; values are clamped. The file is written with a comment explaining each setting.
 */
public final class AcousticSettingsJson {
	/** The 4a key, now the treble loss of the air medium. */
	private static final String OLD_AIR_KEY = "airAbsorptionDbPerBlock";

	private AcousticSettingsJson() {
	}

	/**
	 * What {@link #read} found: the settings, and whether the file held every setting and none of the retired keys.
	 * An incomplete file is rewritten by {@link AcousticConfig}, so it gains the new settings and their comments.
	 */
	public record Result(AcousticSettings settings, boolean complete) {
	}

	/** Collects whether every setting was present in the file being read. */
	private static final class Reading {
		boolean complete = true;

		boolean has(JsonObject parent, String key) {
			boolean present = parent.has(key);
			complete &= present;
			return present;
		}

		JsonObject object(JsonObject parent, String key) {
			return has(parent, key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : new JsonObject();
		}

		double number(JsonObject parent, String key, double fallback) {
			return has(parent, key) ? parent.get(key).getAsDouble() : fallback;
		}

		boolean bool(JsonObject parent, String key, boolean fallback) {
			return has(parent, key) ? parent.get(key).getAsBoolean() : fallback;
		}
	}

	public static Result read(Reader in, Consumer<String> warn) {
		AcousticSettings d = AcousticSettings.DEFAULT;
		try {
			JsonReader reader = new JsonReader(in);
			reader.setStrictness(Strictness.LENIENT);
			JsonElement root = JsonParser.parseReader(reader);
			if (reader.peek() != JsonToken.END_DOCUMENT) {
				throw new JsonParseException("unexpected content after the settings");
			}
			if (!root.isJsonObject()) {
				throw new JsonParseException("not an object");
			}
			JsonObject o = root.getAsJsonObject();
			Reading r = new Reading();
			JsonObject p = r.object(r.object(o, "profiles"), "portable");
			DeviceProfile dp = d.portable();
			AcousticSettings read = new AcousticSettings(
					r.number(o, "rolloff", d.rolloff()),
					r.number(o, "fadeFraction", d.fadeFraction()),
					r.number(o, "audibilityThresholdDb", d.audibilityThresholdDb()),
					r.number(o, "maxRange", d.maxRange()),
					new DeviceProfile(r.number(p, "powerWatts", dp.powerWatts()), r.number(p, "sensitivityDb", dp.sensitivityDb()),
							r.number(p, "bassCutDb", dp.bassCutDb()), r.number(p, "bassCutHz", dp.bassCutHz()), dp.directivity()),
					readOcclusion(r, o, d.occlusion(), warn));
			if (o.has(OLD_AIR_KEY)) {
				r.complete = false;
			}
			return new Result(read.clamped(warn), r.complete);
		} catch (RuntimeException | IOException e) {
			warn.accept("unreadable (" + e.getMessage() + "), using defaults");
			return new Result(d, false);
		}
	}

	private static OcclusionSettings readOcclusion(Reading r, JsonObject root, OcclusionSettings d, Consumer<String> warn) {
		JsonObject o = r.object(root, "occlusion");

		List<AcousticMedium> defaultMedia = new ArrayList<>(d.media());
		boolean fileDefinesAir = o.has("media") && o.get("media").isJsonObject() && o.getAsJsonObject("media").has("air");
		if (root.has(OLD_AIR_KEY) && !fileDefinesAir) {
			// Migration: the old key becomes the treble loss of the air medium, unless the file defines air itself.
			double old = root.get(OLD_AIR_KEY).getAsDouble();
			defaultMedia.replaceAll(m -> m.name().equals("air")
					? new AcousticMedium(m.name(), m.impedance(), m.lossDbPerBlock(), old) : m);
		}
		// Entries keep file order and start from the default of the same name; defaults the file lacks follow them.
		List<AcousticMedium> media = new ArrayList<>();
		for (Map.Entry<String, JsonElement> e : r.object(o, "media").entrySet()) {
			AcousticMedium base = named(defaultMedia, e.getKey(), AcousticMedium::name,
					new AcousticMedium(e.getKey(), 0, 0, 0));
			JsonObject m = e.getValue().isJsonObject() ? e.getValue().getAsJsonObject() : new JsonObject();
			media.add(new AcousticMedium(e.getKey(), r.number(m, "impedance", base.impedance()),
					r.number(m, "lossDbPerBlock", base.lossDbPerBlock()),
					r.number(m, "highLossDbPerBlock", base.highLossDbPerBlock())));
		}
		for (AcousticMedium m : defaultMedia) {
			if (media.stream().noneMatch(x -> x.name().equals(m.name()))) {
				r.complete = false;
				media.add(m);
			}
		}

		List<AcousticMaterial> materials = new ArrayList<>();
		for (Map.Entry<String, JsonElement> e : r.object(o, "materials").entrySet()) {
			AcousticMaterial base = named(d.materials(), e.getKey(), AcousticMaterial::name,
					new AcousticMaterial(e.getKey(), 0, 0));
			JsonObject m = e.getValue().isJsonObject() ? e.getValue().getAsJsonObject() : new JsonObject();
			materials.add(new AcousticMaterial(e.getKey(), r.number(m, "lossDb", base.lossDb()),
					r.number(m, "highLossDb", base.highLossDb())));
		}
		for (AcousticMaterial m : d.materials()) {
			if (materials.stream().noneMatch(x -> x.name().equals(m.name()))) {
				r.complete = false;
				materials.add(m);
			}
		}

		Map<String, String> dimensions = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> e : r.object(o, "dimensions").entrySet()) {
			JsonElement v = e.getValue();
			if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) {
				dimensions.put(e.getKey(), v.getAsString());
			} else {
				warn.accept("occlusion.dimensions." + e.getKey() + " is not a medium name, dropped");
			}
		}

		return new OcclusionSettings(r.bool(o, "enabled", d.enabled()), r.number(o, "maxLossDb", d.maxLossDb()), media,
				r.number(o, "interfaceScale", d.interfaceScale()),
				r.number(o, "interfaceHighRatio", d.interfaceHighRatio()), dimensions, materials,
				r.bool(o, "detours", d.detours()), r.number(o, "bendLossDb", d.bendLossDb()),
				r.number(o, "bendHighLossDb", d.bendHighLossDb()));
	}

	private static <T> T named(List<T> list, String name, Function<T, String> nameOf, T fallback) {
		for (T t : list) {
			if (nameOf.apply(t).equals(name)) {
				return t;
			}
		}
		return fallback;
	}

	/** Writes the settings as a commented file, one comment above each setting. */
	public static void write(AcousticSettings s, Writer out) {
		DeviceProfile p = s.portable();
		String text = """
				// RaveCraft server acoustics. Read when the server starts; restart it after editing.
				// Every player receives these settings when joining, so everyone hears speakers the same way.
				// Values outside their bounds are brought into them (a warning is logged).
				// A file that lacks a setting is rewritten complete at startup; the old one is kept as a .bak copy.
				{
				  // How fast sound fades with distance. 1 = real world: -6 dB each time the distance doubles.
				  // Lower carries further and decays more gently; higher dies off faster. Bounds: 0.25 to 3.
				  "rolloff": %s,

				  // Share of a speaker's range over which it fades smoothly to silence, so it never cuts off.
				  // 0.2 = the last 20 %% of the range. Bounds: 0.05 to 0.5.
				  "fadeFraction": %s,

				  // Sound level (dB SPL) under which a speaker is no longer heard; its range ends there.
				  // Lower makes every speaker carry further. Bounds: 30 to 100.
				  "audibilityThresholdDb": %s,

				  // Longest range any speaker may reach, in blocks, whatever its power. Players this far away
				  // still receive the stream, so a high value costs bandwidth. Bounds: 16 to 512.
				  "maxRange": %s,

				  // Each device is described by its data sheet; its range is computed from it:
				  //   level at 1 m = sensitivityDb + 10 x log10(powerWatts)
				  //   range = distance where that level, falling with "rolloff", reaches "audibilityThresholdDb"
				  // Four times the power doubles the range (with rolloff 1). The speaker's volume scales it down.
				  "profiles": {
				    "portable": {
				      // Amplifier power, in watts. Bounds: 0.1 to 100000.
				      "powerWatts": %s,

				      // Sound level 1 m in front of the speaker for 1 W (dB SPL). Bounds: 70 to 115.
				      "sensitivityDb": %s,

				      // Bass shelf: gain applied below "bassCutHz", in dB. Negative = less bass (small drivers).
				      // 0 keeps the bass intact. Bounds: -24 to 0.
				      "bassCutDb": %s,

				      // Frequency under which the bass shelf applies, in Hz. Bounds: 40 to 500.
				      "bassCutHz": %s
				    }
				  },

				%s}
				""".formatted(n(s.rolloff()), n(s.fadeFraction()), n(s.audibilityThresholdDb()), n(s.maxRange()),
				n(p.powerWatts()), n(p.sensitivityDb()), n(p.bassCutDb()), n(p.bassCutHz()), occlusionText(s.occlusion()));
		try {
			out.write(text);
			out.flush();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static String occlusionText(OcclusionSettings o) {
		StringBuilder b = new StringBuilder();
		b.append("""
				  // How walls, water and other obstacles muffle a speaker. Sound loses level and treble as it travels,
				  // and each obstacle on the straight line to the listener adds its loss (capped by "maxLossDb").
				  // "lossDb" is a loss of the whole sound, in dB; "highLossDb" is the EXTRA loss of the treble on top
				  // of it, so a wall with lossDb 15 and highLossDb 15 takes 30 dB off the highs.
				  "occlusion": {
				    // Turns the whole occlusion model off for everyone when false: walls no longer muffle sound.
				    "enabled": %s,

				    // The most a listener can lose to obstacles, in dB, however many walls are in the way.
				    // Bounds: 20 to 120.
				    "maxLossDb": %s,

				    // What sound travels through: one line per medium, in order, at most 16 (names use a-z, 0-9 and _,
				    // 1 to 32 characters). Air is always present and comes back with its defaults if removed.
				    "media": {
				      //   impedance: how much the medium resists sound; the difference between two media sets the
				      //     loss when sound passes from one into the other (air into water loses a lot).
				      //     Bounds: 0 to 100000000.
				      //   lossDbPerBlock: loss of the whole sound for each block travelled in it. Bounds: 0 to 40.
				      //   highLossDbPerBlock: extra treble loss for each block travelled in it; for air this is the
				      //     old air absorption (far speakers sound duller). Bounds: 0 to 40.
				""".formatted(o.enabled(), n(o.maxLossDb())));
		for (int i = 0; i < o.media().size(); i++) {
			AcousticMedium m = o.media().get(i);
			b.append("      ").append(quote(m.name())).append(": { \"impedance\": ").append(n(m.impedance()))
					.append(", \"lossDbPerBlock\": ").append(n(m.lossDbPerBlock()))
					.append(", \"highLossDbPerBlock\": ").append(n(m.highLossDbPerBlock())).append(" }")
					.append(i < o.media().size() - 1 ? ",\n" : "\n");
		}
		b.append("""
				    },

				    // Share of the impedance loss kept when sound crosses between two media (water surface, lava
				    // surface...). 0 ignores the interface loss, 1 applies it fully. Bounds: 0 to 1.
				    "interfaceScale": %s,

				    // Extra treble lost at such an interface, as a share of the broadband interface loss.
				    // Bounds: 0 to 2.
				    "interfaceHighRatio": %s,

				    // The medium that fills a whole dimension, by dimension id, for example "ad_astra:moon": "vacuum".
				    // Dimensions not listed here are filled with air. At most 32 entries; the medium must exist above.
				    "dimensions": %s,

				    // What solid blocks do to sound: one line per material, in order, at most 32 (names use a-z, 0-9
				    // and _, 1 to 32 characters). The eight below always exist and come back with their defaults if
				    // removed; add your own and assign blocks to them with the acoustic block tags.
				    "materials": {
				      //   lossDb: loss of the whole sound for one block of it. Bounds: 0 to 40.
				      //   highLossDb: extra treble loss for one block of it, on top of lossDb. Bounds: 0 to 40.
				""".formatted(n(o.interfaceScale()), n(o.interfaceHighRatio()), dimensionsText(o.dimensions())));
		for (int i = 0; i < o.materials().size(); i++) {
			AcousticMaterial m = o.materials().get(i);
			b.append("      ").append(quote(m.name())).append(": { \"lossDb\": ").append(n(m.lossDb()))
					.append(", \"highLossDb\": ").append(n(m.highLossDb())).append(" }")
					.append(i < o.materials().size() - 1 ? ",\n" : "\n");
		}
		b.append("""
				    },

				    // When a wall blocks the straight line, sound can still reach the listener through a nearby
				    // opening (door, window, gap). true looks for such detours; false only uses the straight line.
				    "detours": %s,

				    // Loss added for each bend of a detour, in dB. Bounds: 0 to 40.
				    "bendLossDb": %s,

				    // Extra treble loss added for each bend of a detour, on top of bendLossDb. Bounds: 0 to 40.
				    "bendHighLossDb": %s
				  }
				""".formatted(o.detours(), n(o.bendLossDb()), n(o.bendHighLossDb())));
		return b.toString();
	}

	private static String dimensionsText(Map<String, String> dimensions) {
		if (dimensions.isEmpty()) {
			return "{ }";
		}
		StringBuilder b = new StringBuilder("{\n");
		int i = 0;
		for (Map.Entry<String, String> e : dimensions.entrySet()) {
			b.append("      ").append(quote(e.getKey())).append(": ").append(quote(e.getValue()))
					.append(++i < dimensions.size() ? ",\n" : "\n");
		}
		return b.append("    }").toString();
	}

	private static String quote(String s) {
		return new JsonPrimitive(s).toString();
	}

	/** A number as JSON writes it: no trailing ".0" on whole values, never a locale decimal comma. */
	private static String n(double value) {
		return value == Math.rint(value) && Math.abs(value) < 1e15 ? Long.toString((long) value) : Double.toString(value);
	}
}
