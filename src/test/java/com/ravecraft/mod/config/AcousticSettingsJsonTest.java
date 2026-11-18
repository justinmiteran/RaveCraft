package com.ravecraft.mod.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ravecraft.mod.core.acoustics.AcousticSettings;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AcousticSettingsJsonTest {
	private final List<String> warnings = new ArrayList<>();

	private AcousticSettings read(String json) {
		return AcousticSettingsJson.read(new StringReader(json), warnings::add);
	}

	@Test
	void roundTrip() {
		StringWriter out = new StringWriter();
		AcousticSettingsJson.write(AcousticSettings.DEFAULT, out);
		assertEquals(AcousticSettings.DEFAULT, read(out.toString()));
		assertEquals(List.of(), warnings);
	}

	@Test
	void missingFieldsUseDefaults() {
		AcousticSettings settings = read("{\"rolloff\":0.5}");
		assertEquals(0.5, settings.rolloff());
		assertEquals(AcousticSettings.DEFAULT.fadeFraction(), settings.fadeFraction());
		assertEquals(AcousticSettings.DEFAULT.portable(), settings.portable());
	}

	@Test
	void corruptFileGivesDefaults() {
		assertEquals(AcousticSettings.DEFAULT, read("{not json"));
		assertEquals(1, warnings.size(), warnings.toString());
	}

	@Test
	void outOfRangeIsClamped() {
		assertEquals(115, read("{\"profiles\":{\"portable\":{\"sensitivityDb\":999}}}").portable().sensitivityDb());
	}

	@Test
	void readsNewFields() {
		AcousticSettings settings = read("{\"audibilityThresholdDb\":60,\"maxRange\":128,"
				+ "\"profiles\":{\"portable\":{\"powerWatts\":40,\"sensitivityDb\":90}}}");
		assertEquals(60, settings.audibilityThresholdDb());
		assertEquals(128, settings.maxRange());
		assertEquals(40, settings.portable().powerWatts());
		assertEquals(90, settings.portable().sensitivityDb());
	}

	@Test
	void acceptsComments() {
		AcousticSettings settings = read("""
				{
				  // a line comment
				  "rolloff": 0.5, /* a block comment */
				  "profiles": { "portable": { "powerWatts": 40 } }
				}
				""");
		assertEquals(0.5, settings.rolloff());
		assertEquals(40, settings.portable().powerWatts());
		assertEquals(List.of(), warnings);
	}

	@Test
	void defaultFileExplainsEverySetting() {
		StringWriter out = new StringWriter();
		AcousticSettingsJson.write(AcousticSettings.DEFAULT, out);
		String text = out.toString();
		for (String key : List.of("rolloff", "fadeFraction", "airAbsorptionDbPerBlock", "audibilityThresholdDb", "maxRange",
				"powerWatts", "sensitivityDb", "bassCutDb", "bassCutHz")) {
			int at = text.indexOf("\"" + key + "\":");
			assertTrue(at > 0, key + " missing");
			String before = text.substring(0, text.lastIndexOf('\n', at)).stripTrailing();
			String lineAbove = before.substring(before.lastIndexOf('\n') + 1).trim();
			assertTrue(lineAbove.startsWith("//"), key + " has no comment line above it");
		}
	}
}
