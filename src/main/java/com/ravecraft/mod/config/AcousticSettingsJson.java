package com.ravecraft.mod.config;

import com.google.gson.Strictness;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.ravecraft.mod.core.acoustics.AcousticSettings;
import com.ravecraft.mod.core.acoustics.DeviceProfile;
import com.ravecraft.mod.core.acoustics.DeviceProfiles;
import com.ravecraft.mod.core.acoustics.occlusion.AcousticMaterial;
import com.ravecraft.mod.core.acoustics.occlusion.AcousticMedium;
import com.ravecraft.mod.core.acoustics.occlusion.OcclusionSettings;
import com.ravecraft.mod.core.acoustics.reverb.EchoSettings;
import com.ravecraft.mod.core.acoustics.reverb.FluidReverb;
import com.ravecraft.mod.core.acoustics.reverb.ReverbSettings;
import com.ravecraft.mod.core.speaker.SpeakerKind;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Reads and writes {@code config/ravecraft/server.json5}: JSON that may hold line and block comments. A missing
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

	/**
	 * Reads settings, each missing key taking its default; an unreadable file gives the defaults.
	 *
	 * @param in the file's text
	 * @param warn receives a warning for each bad key
	 * @return the settings read, with the warnings
	 */
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
			JsonObject profiles = r.object(o, "profiles");
			AcousticSettings read = new AcousticSettings(
					r.number(o, "rolloff", d.rolloff()),
					r.number(o, "fadeFraction", d.fadeFraction()),
					r.number(o, "audibilityThresholdDb", d.audibilityThresholdDb()),
					r.number(o, "maxRange", d.maxRange()),
					new DeviceProfiles(readProfile(r, r.object(profiles, SpeakerKind.PORTABLE.id()), d.profiles().portable()),
							readProfile(r, r.object(profiles, SpeakerKind.PARTY.id()), d.profiles().party())),
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

	/** Reads one speaker profile, each missing key taking its value in {@code d}. */
	private static DeviceProfile readProfile(Reading r, JsonObject p, DeviceProfile d) {
		return new DeviceProfile(r.number(p, "powerWatts", d.powerWatts()), r.number(p, "sensitivityDb", d.sensitivityDb()),
				r.number(p, "bassCutDb", d.bassCutDb()), r.number(p, "bassCutHz", d.bassCutHz()));
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

	/**
	 * Writes the settings as a commented file, one comment above each setting.
	 *
	 * @param s the settings to write
	 * @param out where the file goes
	 */
	public static void write(AcousticSettings s, Writer out) {
		AcousticSettingsWriter.write(s, out);
	}
}
