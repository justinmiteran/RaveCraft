package com.ravecraft.mod.core.speaker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SpeakerSettingsTest {
	@Test
	void defaultIsOffAtSeventy() {
		assertEquals(new SpeakerSettings(false, 70), SpeakerSettings.DEFAULT);
	}

	@Test
	void volumeIsClamped() {
		assertEquals(0, new SpeakerSettings(true, -5).volume());
		assertEquals(100, new SpeakerSettings(true, 250).volume());
		assertEquals(100, SpeakerSettings.DEFAULT.withVolume(101).volume());
	}

	@Test
	void togglePowerFlipsOnlyPower() {
		SpeakerSettings on = SpeakerSettings.DEFAULT.withVolume(40).togglePower();
		assertEquals(new SpeakerSettings(true, 40), on);
		assertEquals(new SpeakerSettings(false, 40), on.togglePower());
	}
}
