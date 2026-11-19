package com.ravecraft.mod.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ravecraft.mod.core.acoustics.AcousticSettings;
import com.ravecraft.mod.core.acoustics.occlusion.AcousticMaterial;
import com.ravecraft.mod.core.acoustics.occlusion.AcousticMedium;
import com.ravecraft.mod.core.acoustics.occlusion.OcclusionSettings;
import com.ravecraft.mod.core.acoustics.reverb.EchoSettings;
import com.ravecraft.mod.core.acoustics.reverb.FluidReverb;
import com.ravecraft.mod.core.acoustics.reverb.ReverbSettings;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AcousticSettingsJsonTest {
	private final List<String> warnings = new ArrayList<>();

	private AcousticSettingsJson.Result result(String json) {
		return AcousticSettingsJson.read(new StringReader(json), warnings::add);
	}

	private AcousticSettings read(String json) {
		return result(json).settings();
	}

	private static AcousticMaterial material(OcclusionSettings o, String name) {
		return o.materials().stream().filter(m -> m.name().equals(name)).findFirst().orElseThrow();
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
		AcousticSettingsJson.Result r = result("{not json");
		assertEquals(AcousticSettings.DEFAULT, r.settings());
		assertTrue(r.complete(), "a broken file must not be rewritten");
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
		for (String key : List.of("rolloff", "fadeFraction", "audibilityThresholdDb", "maxRange",
				"powerWatts", "sensitivityDb", "bassCutDb", "bassCutHz", "occlusion", "enabled", "maxLossDb", "media",
				"impedance", "lossDbPerBlock", "highLossDbPerBlock", "interfaceScale", "interfaceHighRatio",
				"dimensions", "materials", "lossDb", "highLossDb", "detours", "bendLossDb", "bendHighLossDb")) {
			int at = text.indexOf("\"" + key + "\":");
			assertTrue(at > 0, key + " missing");
			String before = text.substring(0, text.lastIndexOf('\n', at)).stripTrailing();
			String lineAbove = before.substring(before.lastIndexOf('\n') + 1).trim();
			assertTrue(lineAbove.startsWith("//"), key + " has no comment line above it");
		}
		assertFalse(text.contains("airAbsorptionDbPerBlock"));
	}

	@Test
	void readsOcclusionSection() {
		AcousticSettingsJson.Result r = result("""
				{ "occlusion": { "maxLossDb": 40,
				  "materials": { "stone": { "lossDb": 10, "highLossDb": 5 }, "rubber": { "lossDb": 25, "highLossDb": 30 } },
				  "dimensions": { "ad_astra:moon": "vacuum" } } }
				""");
		OcclusionSettings o = r.settings().occlusion();
		assertEquals(40, o.maxLossDb());
		assertEquals(new AcousticMaterial("stone", 10, 5, 0.02), material(o, "stone"));
		assertEquals(new AcousticMaterial("rubber", 25, 30), material(o, "rubber"));
		assertEquals("vacuum", o.dimensionMedium("ad_astra:moon").name());
		assertFalse(r.complete());
	}

	@Test
	void oldFileMigratesAndAsksRewrite() {
		AcousticSettingsJson.Result r = result("{\"airAbsorptionDbPerBlock\": 0.1}");
		assertEquals(0.1, r.settings().occlusion().air().highLossDbPerBlock());
		assertFalse(r.complete());
	}

	@Test
	void migratedOldAirKeyKeepsItsOldRange() {
		assertEquals(1, result("{\"airAbsorptionDbPerBlock\": 5}").settings().occlusion().air().highLossDbPerBlock());
		assertEquals(0, result("{\"airAbsorptionDbPerBlock\": -2}").settings().occlusion().air().highLossDbPerBlock());
	}

	@Test
	void mediaAirWinsOverTheOldKey() {
		AcousticSettingsJson.Result r = result("""
				{ "airAbsorptionDbPerBlock": 0.1,
				  "occlusion": { "media": { "air": { "impedance": 415, "lossDbPerBlock": 0, "highLossDbPerBlock": 0.3 } } } }
				""");
		assertEquals(0.3, r.settings().occlusion().air().highLossDbPerBlock());
		assertFalse(r.complete());
	}

	@Test
	void writtenDefaultsAreComplete() {
		StringWriter out = new StringWriter();
		AcousticSettingsJson.write(AcousticSettings.DEFAULT, out);
		AcousticSettingsJson.Result r = result(out.toString());
		assertEquals(AcousticSettings.DEFAULT, r.settings());
		assertTrue(r.complete());
	}

	@Test
	void customListsRoundTrip() {
		OcclusionSettings d = OcclusionSettings.DEFAULT;
		List<AcousticMaterial> materials = new ArrayList<>(d.materials());
		materials.add(new AcousticMaterial("rubber", 25, 30, 0.6));
		List<AcousticMedium> media = new ArrayList<>(d.media());
		media.add(new AcousticMedium("slime", 2_000_000, 1.5, 2.5, 7, 9, 0.5, 0.3));
		OcclusionSettings custom = new OcclusionSettings(d.enabled(), d.maxLossDb(), media, d.interfaceScale(),
				d.interfaceHighRatio(), Map.of("ad_astra:moon", "vacuum"), materials, d.detours(), d.bendLossDb(),
				d.bendHighLossDb());
		AcousticSettings s = AcousticSettings.DEFAULT;
		AcousticSettings withCustom = new AcousticSettings(s.rolloff(), s.fadeFraction(), s.audibilityThresholdDb(),
				s.maxRange(), s.portable(), custom, s.reverb());
		StringWriter out = new StringWriter();
		AcousticSettingsJson.write(withCustom, out);
		AcousticSettingsJson.Result r = result(out.toString());
		assertEquals(withCustom, r.settings());
		assertTrue(r.complete());
	}

	@Test
	void reverbRoundTrips() {
		List<FluidReverb> media = new ArrayList<>(ReverbSettings.DEFAULT.media());
		media.add(new FluidReverb("slime", 2.5, 0.5, 0.6, 0.2, 0.7, true, 0.8, 3));
		ReverbSettings custom = new ReverbSettings(false, 90, 8, 0.5, 12,
				new EchoSettings(20, 30, 6, 0.3, 0.4, 0.25, 0.7), media);
		AcousticSettings s = AcousticSettings.DEFAULT;
		AcousticSettings withCustom = new AcousticSettings(s.rolloff(), s.fadeFraction(), s.audibilityThresholdDb(),
				s.maxRange(), s.portable(), s.occlusion(), custom);
		StringWriter out = new StringWriter();
		AcousticSettingsJson.write(withCustom, out);
		AcousticSettingsJson.Result r = result(out.toString());
		assertEquals(withCustom, r.settings());
		assertTrue(r.complete());
	}

	@Test
	void fileWithoutReverbIsIncompleteAndTakesDefaults() {
		AcousticSettingsJson.Result r = result("{ \"rolloff\": 1 }");
		assertEquals(ReverbSettings.DEFAULT, r.settings().reverb());
		assertFalse(r.complete());
	}

	@Test
	void fileWithoutAbsorptionIsIncompleteAndTakesDefaults() {
		AcousticSettingsJson.Result r = result("""
				{ "occlusion": {
				  "media": { "water": { "impedance": 1480000, "lossDbPerBlock": 0, "highLossDbPerBlock": 0.02 } },
				  "materials": { "stone": { "lossDb": 15, "highLossDb": 30 } } } }
				""");
		OcclusionSettings o = r.settings().occlusion();
		assertEquals(0.02, material(o, "stone").absorption());
		assertEquals(0.01, o.medium("water").surfaceAbsorption());
		assertFalse(r.complete());
	}
}
