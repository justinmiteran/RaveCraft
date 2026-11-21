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
import com.ravecraft.mod.core.acoustics.reverb.EchoSettings;
import com.ravecraft.mod.core.acoustics.reverb.FluidReverb;
import com.ravecraft.mod.core.acoustics.reverb.ReverbSettings;
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

	/** Reads settings, each missing key taking its default; an unreadable file gives the defaults. */
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
					readOcclusion(r, o, d.occlusion(), warn),
					readReverb(r, o, d.reverb()));
			if (o.has(OLD_AIR_KEY)) {
				r.complete = false;
			}
			return new Result(read.clamped(warn), r.complete);
		} catch (RuntimeException | IOException e) {
			// Complete on purpose: a broken file is never rewritten, the admin fixes it and keeps their settings.
			warn.accept("unreadable (" + e.getMessage() + "), using the defaults until the file is fixed");
			return new Result(d, true);
		}
	}

	/** Reads the {@code occlusion} section, each missing key taking its value in {@code d}. */
	private static OcclusionSettings readOcclusion(Reading r, JsonObject root, OcclusionSettings d, Consumer<String> warn) {
		JsonObject o = r.object(root, "occlusion");
		List<AcousticMedium> media = readMedia(r, o, defaultMedia(root, o, d, warn));
		List<AcousticMaterial> materials = readMaterials(r, o, d);
		Map<String, String> dimensions = readDimensions(r, o, warn);
		return new OcclusionSettings(r.bool(o, "enabled", d.enabled()), r.number(o, "maxLossDb", d.maxLossDb()), media,
				r.number(o, "interfaceScale", d.interfaceScale()),
				r.number(o, "interfaceHighRatio", d.interfaceHighRatio()), dimensions, materials,
				r.bool(o, "detours", d.detours()), r.number(o, "bendLossDb", d.bendLossDb()),
				r.number(o, "bendHighLossDb", d.bendHighLossDb()));
	}

	/** The default media, with the treble loss of air migrated from {@link #OLD_AIR_KEY} when the file has it. */
	private static List<AcousticMedium> defaultMedia(JsonObject root, JsonObject o, OcclusionSettings d,
			Consumer<String> warn) {
		List<AcousticMedium> defaultMedia = new ArrayList<>(d.media());
		boolean fileDefinesAir = o.has("media") && o.get("media").isJsonObject() && o.getAsJsonObject("media").has("air");
		if (root.has(OLD_AIR_KEY) && !fileDefinesAir) {
			// Migration: the old key becomes the treble loss of the air medium, unless the file defines air itself. It
			// keeps the 4a range [0, 1], narrower than a medium's.
			double raw = root.get(OLD_AIR_KEY).getAsDouble();
			double old = Double.isNaN(raw) ? 0 : Math.clamp(raw, 0, 1);
			if (old != raw) {
				warn.accept(OLD_AIR_KEY + " = " + raw + " is out of [0, 1], using " + old);
			}
			defaultMedia.replaceAll(m -> m.name().equals("air")
					? new AcousticMedium(m.name(), m.impedance(), m.lossDbPerBlock(), old) : m);
		}
		return defaultMedia;
	}

	/** The media of the {@code occlusion} section {@code o}, starting from {@code defaultMedia}. */
	private static List<AcousticMedium> readMedia(Reading r, JsonObject o, List<AcousticMedium> defaultMedia) {
		// Entries keep file order and start from the default of the same name; defaults the file lacks follow them.
		List<AcousticMedium> media = new ArrayList<>();
		for (Map.Entry<String, JsonElement> e : r.object(o, "media").entrySet()) {
			AcousticMedium base = named(defaultMedia, e.getKey(), AcousticMedium::name,
					new AcousticMedium(e.getKey(), 0, 0, 0));
			JsonObject m = e.getValue().isJsonObject() ? e.getValue().getAsJsonObject() : new JsonObject();
			media.add(new AcousticMedium(e.getKey(), r.number(m, "impedance", base.impedance()),
					r.number(m, "lossDbPerBlock", base.lossDbPerBlock()),
					r.number(m, "highLossDbPerBlock", base.highLossDbPerBlock()),
					r.number(m, "listenerLossDb", base.listenerLossDb()),
					r.number(m, "listenerHighLossDb", base.listenerHighLossDb()),
					r.number(m, "listenerBlur", base.listenerBlur()),
					r.number(m, "surfaceAbsorption", base.surfaceAbsorption())));
		}
		for (AcousticMedium m : defaultMedia) {
			if (media.stream().noneMatch(x -> x.name().equals(m.name()))) {
				r.complete = false;
				media.add(m);
			}
		}
		return media;
	}

	/** The materials of the {@code occlusion} section {@code o}, starting from those of {@code d}. */
	private static List<AcousticMaterial> readMaterials(Reading r, JsonObject o, OcclusionSettings d) {
		List<AcousticMaterial> materials = new ArrayList<>();
		for (Map.Entry<String, JsonElement> e : r.object(o, "materials").entrySet()) {
			AcousticMaterial base = named(d.materials(), e.getKey(), AcousticMaterial::name,
					new AcousticMaterial(e.getKey(), 0, 0));
			JsonObject m = e.getValue().isJsonObject() ? e.getValue().getAsJsonObject() : new JsonObject();
			materials.add(new AcousticMaterial(e.getKey(), r.number(m, "lossDb", base.lossDb()),
					r.number(m, "highLossDb", base.highLossDb()),
					r.number(m, "absorption", base.absorption())));
		}
		for (AcousticMaterial m : d.materials()) {
			if (materials.stream().noneMatch(x -> x.name().equals(m.name()))) {
				r.complete = false;
				materials.add(m);
			}
		}
		return materials;
	}

	/** The medium of each dimension named in the {@code occlusion} section {@code o}; drops the others. */
	private static Map<String, String> readDimensions(Reading r, JsonObject o, Consumer<String> warn) {
		Map<String, String> dimensions = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> e : r.object(o, "dimensions").entrySet()) {
			JsonElement v = e.getValue();
			if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) {
				dimensions.put(e.getKey(), v.getAsString());
			} else {
				warn.accept("occlusion.dimensions." + e.getKey() + " is not a medium name, dropped");
			}
		}
		return dimensions;
	}

	/** Reads the {@code reverb} section, each missing key taking its value in {@code d}. */
	private static ReverbSettings readReverb(Reading r, JsonObject root, ReverbSettings d) {
		JsonObject o = r.object(root, "reverb");

		EchoSettings de = d.echo();
		JsonObject e = r.object(o, "echo");
		EchoSettings echo = new EchoSettings(r.number(e, "minDistance", de.minDistance()),
				r.number(e, "maxDistance", de.maxDistance()), (int) r.number(e, "minRays", de.minRays()),
				r.number(e, "maxAbsorption", de.maxAbsorption()), r.number(e, "gain", de.gain()),
				r.number(e, "feedback", de.feedback()), r.number(e, "damping", de.damping()));

		// Entries keep file order and start from the default of the same name; defaults the file lacks follow them.
		List<FluidReverb> media = new ArrayList<>();
		for (Map.Entry<String, JsonElement> entry : r.object(o, "media").entrySet()) {
			FluidReverb base = named(d.media(), entry.getKey(), FluidReverb::medium,
					new FluidReverb(entry.getKey(), 1, 1, 1, 0.3, 1, false, 0.4, 1.5));
			JsonObject m = entry.getValue().isJsonObject() ? entry.getValue().getAsJsonObject() : new JsonObject();
			media.add(new FluidReverb(entry.getKey(), r.number(m, "decaySeconds", base.decaySeconds()),
					r.number(m, "density", base.density()), r.number(m, "diffusion", base.diffusion()),
					r.number(m, "gain", base.gain()), r.number(m, "decayHfRatio", base.decayHfRatio()),
					r.bool(m, "chorus", base.chorus()), r.number(m, "chorusRateHz", base.chorusRateHz()),
					r.number(m, "chorusDepthMs", base.chorusDepthMs())));
		}
		for (FluidReverb f : d.media()) {
			if (media.stream().noneMatch(x -> x.medium().equals(f.medium()))) {
				r.complete = false;
				media.add(f);
			}
		}

		return new ReverbSettings(r.bool(o, "enabled", d.enabled()), r.number(o, "rayLength", d.rayLength()),
				r.number(o, "maxDecaySeconds", d.maxDecaySeconds()), r.number(o, "maxWetGain", d.maxWetGain()),
				r.number(o, "sameSpaceLossDb", d.sameSpaceLossDb()), echo, media);
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

				%s%s}
				""".formatted(n(s.rolloff()), n(s.fadeFraction()), n(s.audibilityThresholdDb()), n(s.maxRange()),
				n(p.powerWatts()), n(p.sensitivityDb()), n(p.bassCutDb()), n(p.bassCutHz()), occlusionText(s.occlusion()),
				reverbText(s.reverb()));
		try {
			out.write(text);
			out.flush();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** The {@code occlusion} section as JSON text. */
	private static String occlusionText(OcclusionSettings o) {
		StringBuilder b = new StringBuilder();
		b.append("""
				  // How walls, water and other obstacles muffle a speaker. Sound loses level and treble as it travels,
				  // and each obstacle on the straight line to the listener adds its loss (capped by "maxLossDb").
				  // "lossDb" is a loss of the whole sound, in dB; "highLossDb" is the EXTRA loss at 4 kHz on top of
				  // it, made by a low-pass falling 6 dB per octave (like a real wall): with lossDb 15 and highLossDb 30,
				  // a wall takes about 17 dB off at 100 Hz and 45 dB off at 4 kHz, so mostly the bass gets through.
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
				      //   listenerLossDb, listenerHighLossDb, listenerBlur: how a player whose head is in this medium
				      //     hears every speaker. A human ear underwater hears through the skull: quieter
				      //     (listenerLossDb, 0 to 60), duller (listenerHighLossDb, extra loss at 4 kHz, 0 to 40) and
				      //     without direction (listenerBlur: 0 sharp, 1 the sound seems to come from everywhere).
				      //   surfaceAbsorption: share of the sound absorbed by the surface of this medium seen from
				      //     another one, for the room reverb (water reflects, open air swallows). Bounds: 0 to 1.
				""".formatted(o.enabled(), n(o.maxLossDb())));
		for (int i = 0; i < o.media().size(); i++) {
			AcousticMedium m = o.media().get(i);
			b.append("      ").append(quote(m.name())).append(": { \"impedance\": ").append(n(m.impedance()))
					.append(", \"lossDbPerBlock\": ").append(n(m.lossDbPerBlock()))
					.append(", \"highLossDbPerBlock\": ").append(n(m.highLossDbPerBlock()))
					.append(",\n        \"listenerLossDb\": ").append(n(m.listenerLossDb()))
					.append(", \"listenerHighLossDb\": ").append(n(m.listenerHighLossDb()))
					.append(", \"listenerBlur\": ").append(n(m.listenerBlur()))
					.append(", \"surfaceAbsorption\": ").append(n(m.surfaceAbsorption())).append(" }")
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
				      //   absorption: share of the sound a surface of it absorbs instead of reflecting into the room,
				      //     for the room reverb (stone rings, wool deadens). Bounds: 0 to 1.
				""".formatted(n(o.interfaceScale()), n(o.interfaceHighRatio()), dimensionsText(o.dimensions())));
		for (int i = 0; i < o.materials().size(); i++) {
			AcousticMaterial m = o.materials().get(i);
			b.append("      ").append(quote(m.name())).append(": { \"lossDb\": ").append(n(m.lossDb()))
					.append(", \"highLossDb\": ").append(n(m.highLossDb()))
					.append(", \"absorption\": ").append(n(m.absorption())).append(" }")
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
				  },

				""".formatted(o.detours(), n(o.bendLossDb()), n(o.bendHighLossDb())));
		return b.toString();
	}

	/** The {@code reverb} section as JSON text. */
	private static String reverbText(ReverbSettings v) {
		EchoSettings e = v.echo();
		StringBuilder b = new StringBuilder();
		b.append("""
				  // How rooms ring. A speaker in an enclosed space gets a reverb that grows with the size of the room
				  // and shrinks with the absorption of its walls (see "absorption" in the occlusion materials), and a
				  // far hard wall can give a distinct echo. Underwater and in lava the reverb is that of the fluid.
				  "reverb": {
				    // Turns the whole reverb off for everyone when false: no room tail, echo or fluid reverb.
				    "enabled": %s,

				    // How far the rays that measure a room travel, in blocks. A room larger than this sounds like
				    // open air. Longer is more precise and costs more. Bounds: 16 to 128.
				    "rayLength": %s,

				    // The longest reverb tail, in seconds, however large and hard the room. Bounds: 0.5 to 20.
				    "maxDecaySeconds": %s,

				    // The loudest the reverb may be, as a share of the dry sound. Bounds: 0 to 1.
				    "maxWetGain": %s,

				    // A speaker whose sound loses less than this many dB through walls on its way to the listener
				    // is in the listener's room and shares its reverb; above it, it rings with its own room's
				    // reverb. Bounds: 0 to 60.
				    "sameSpaceLossDb": %s,

				    // The distinct echo thrown back by a far, hard surface.
				    "echo": {
				      // Nearest surface that gives an echo, in blocks. Bounds: 10 to 35.
				      "minDistance": %s,

				      // Farthest surface that gives an echo, in blocks; never below minDistance. Bounds: minDistance to 35.
				      "maxDistance": %s,

				      // Rays that must come back from such a surface for the echo to be heard. Bounds: 1 to 16.
				      "minRays": %s,

				      // Surfaces absorbing more than this share give no echo. Bounds: 0 to 1.
				      "maxAbsorption": %s,

				      // Loudness of the echo. Bounds: 0 to 1.
				      "gain": %s,

				      // Share of the echo fed back into itself: more repeats. Bounds: 0 to 0.5.
				      "feedback": %s,

				      // How much each repeat loses its treble. Bounds: 0 to 0.99.
				      "damping": %s
				    },

				    // The reverb heard with the head in a fluid: one line per medium name (see the occlusion media),
				    // in order, at most 16 (names use a-z, 0-9 and _, 1 to 32 characters).
				    "media": {
				      //   decaySeconds: length of the tail. Bounds: 0.1 to 20.
				      //   density, diffusion: how thick and how smooth the tail is. Bounds: 0 to 1.
				      //   gain: loudness of the reverb. Bounds: 0 to 1.
				      //   decayHfRatio: how long the treble lasts compared to the rest (below 1 = duller). Bounds: 0.1 to 2.
				      //   chorus: true adds a slow wobble to the sound (water). chorusRateHz: its speed, 0.05 to 5.
				      //     chorusDepthMs: its depth, 0 to 10.
				""".formatted(v.enabled(), n(v.rayLength()), n(v.maxDecaySeconds()), n(v.maxWetGain()),
				n(v.sameSpaceLossDb()), n(e.minDistance()), n(e.maxDistance()), e.minRays(), n(e.maxAbsorption()),
				n(e.gain()), n(e.feedback()), n(e.damping())));
		for (int i = 0; i < v.media().size(); i++) {
			FluidReverb f = v.media().get(i);
			b.append("      ").append(quote(f.medium())).append(": { \"decaySeconds\": ").append(n(f.decaySeconds()))
					.append(", \"density\": ").append(n(f.density()))
					.append(", \"diffusion\": ").append(n(f.diffusion()))
					.append(", \"gain\": ").append(n(f.gain()))
					.append(",\n        \"decayHfRatio\": ").append(n(f.decayHfRatio()))
					.append(", \"chorus\": ").append(f.chorus())
					.append(", \"chorusRateHz\": ").append(n(f.chorusRateHz()))
					.append(", \"chorusDepthMs\": ").append(n(f.chorusDepthMs())).append(" }")
					.append(i < v.media().size() - 1 ? ",\n" : "\n");
		}
		b.append("    }\n  }\n");
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
