package com.ravecraft.mod.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
		assertEquals(128, read("{\"profiles\":{\"portable\":{\"range\":999}}}").portable().range());
	}
}
