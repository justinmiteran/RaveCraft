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
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.function.Consumer;

/**
 * Reads and writes {@code ravecraft-server.json5}: JSON that may hold line and block comments. A missing
 * field takes its default; values are clamped. The file is written with a comment explaining each setting.
 */
public final class AcousticSettingsJson {
	private AcousticSettingsJson() {
	}

	public static AcousticSettings read(Reader in, Consumer<String> warn) {
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
			JsonObject p = object(object(o, "profiles"), "portable");
			DeviceProfile dp = d.portable();
			AcousticSettings read = new AcousticSettings(
					number(o, "rolloff", d.rolloff()),
					number(o, "fadeFraction", d.fadeFraction()),
					number(o, "airAbsorptionDbPerBlock", d.airAbsorptionDbPerBlock()),
					number(o, "audibilityThresholdDb", d.audibilityThresholdDb()),
					number(o, "maxRange", d.maxRange()),
					new DeviceProfile(number(p, "powerWatts", dp.powerWatts()), number(p, "sensitivityDb", dp.sensitivityDb()),
							number(p, "bassCutDb", dp.bassCutDb()), number(p, "bassCutHz", dp.bassCutHz()), dp.directivity()));
			return read.clamped(warn);
		} catch (RuntimeException | IOException e) {
			warn.accept("unreadable (" + e.getMessage() + "), using defaults");
			return d;
		}
	}

	/** Writes the settings as a commented file, one comment above each setting. */
	public static void write(AcousticSettings s, Writer out) {
		DeviceProfile p = s.portable();
		String text = """
				// RaveCraft server acoustics. Read when the server starts; restart it after editing.
				// Every player receives these settings when joining, so everyone hears speakers the same way.
				// Values outside their bounds are brought back into them (a warning is logged).
				{
				  // How fast sound fades with distance. 1 = real world: -6 dB each time the distance doubles.
				  // Lower carries further and decays more gently; higher dies off faster. Bounds: 0.25 to 3.
				  "rolloff": %s,

				  // Share of a speaker's range over which it fades smoothly to silence, so it never cuts off.
				  // 0.2 = the last 20 %% of the range. Bounds: 0.05 to 0.5.
				  "fadeFraction": %s,

				  // How much of the treble the air absorbs, in dB per block: far speakers sound duller.
				  // 0 turns it off. Bounds: 0 to 1.
				  "airAbsorptionDbPerBlock": %s,

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
				  }
				}
				""".formatted(n(s.rolloff()), n(s.fadeFraction()), n(s.airAbsorptionDbPerBlock()), n(s.audibilityThresholdDb()),
				n(s.maxRange()), n(p.powerWatts()), n(p.sensitivityDb()), n(p.bassCutDb()), n(p.bassCutHz()));
		try {
			out.write(text);
			out.flush();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** A number as JSON writes it: no trailing ".0" on whole values, never a locale decimal comma. */
	private static String n(double value) {
		return value == Math.rint(value) && Math.abs(value) < 1e15 ? Long.toString((long) value) : Double.toString(value);
	}

	private static JsonObject object(JsonObject parent, String key) {
		return parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : new JsonObject();
	}

	private static double number(JsonObject parent, String key, double fallback) {
		return parent.has(key) ? parent.get(key).getAsDouble() : fallback;
	}
}
