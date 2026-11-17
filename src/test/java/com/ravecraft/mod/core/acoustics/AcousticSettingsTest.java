package com.ravecraft.mod.core.acoustics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AcousticSettingsTest {
	@Test
	void defaultsUntouchedByClamp() {
		List<String> warnings = new ArrayList<>();
		assertEquals(AcousticSettings.DEFAULT, AcousticSettings.DEFAULT.clamped(warnings::add));
		assertEquals(List.of(), warnings);
	}

	@Test
	void clampsEveryField() {
		List<String> warnings = new ArrayList<>();
		AcousticSettings wild = new AcousticSettings(10, 0, -1, new DeviceProfile(1000, 5, 1, Directivity.OMNI));
		AcousticSettings clamped = wild.clamped(warnings::add);
		assertEquals(new AcousticSettings(3, 0.05, 0, new DeviceProfile(128, 0, 40, Directivity.OMNI)), clamped);
		assertEquals(6, warnings.size(), warnings.toString());
	}
}
