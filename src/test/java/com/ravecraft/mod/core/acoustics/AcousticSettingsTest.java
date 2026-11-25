package com.ravecraft.mod.core.acoustics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ravecraft.mod.core.acoustics.occlusion.OcclusionSettings;
import com.ravecraft.mod.core.acoustics.reverb.ReverbSettings;
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
		AcousticSettings wild = new AcousticSettings(10, 0, 5, 2000,
				new DeviceProfiles(new DeviceProfile(0, 200, 5, 1), DeviceProfile.PARTY),
				OcclusionSettings.DEFAULT,
				ReverbSettings.DEFAULT);
		AcousticSettings clamped = wild.clamped(warnings::add);
		assertEquals(new AcousticSettings(3, 0.05, 30, 512,
				new DeviceProfiles(new DeviceProfile(0.1, 115, 0, 40), DeviceProfile.PARTY),
				OcclusionSettings.DEFAULT, ReverbSettings.DEFAULT), clamped);
		assertEquals(8, warnings.size(), warnings.toString());
	}

	@Test
	void clampsOcclusionToo() {
		OcclusionSettings d = OcclusionSettings.DEFAULT;
		OcclusionSettings loud = new OcclusionSettings(d.enabled(), 500, d.media(), d.interfaceScale(),
				d.interfaceHighRatio(), d.dimensions(), d.materials(), d.detours(), d.bendLossDb(), d.bendHighLossDb());
		AcousticSettings s = AcousticSettings.DEFAULT;
		AcousticSettings wild = new AcousticSettings(s.rolloff(), s.fadeFraction(), s.audibilityThresholdDb(),
				s.maxRange(), s.profiles(), loud, s.reverb());
		assertEquals(120, wild.clamped(w -> { }).occlusion().maxLossDb());
	}

	@Test
	void airAbsorptionComesFromTheAirMedium() {
		assertEquals(0.05, AcousticSettings.DEFAULT.airAbsorptionDbPerBlock());
	}
}
