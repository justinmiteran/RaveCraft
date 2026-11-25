package com.ravecraft.mod.core.acoustics.radiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ravecraft.mod.core.speaker.SpeakerKind;
import org.junit.jupiter.api.Test;

class RadiationSummaryTest {
	@Test
	void partyCoversAHundredDegrees() {
		assertEquals(new RadiationSummary(false, 100, 12),
				RadiationSummary.of(RadiationPattern.of(Cabinets.of(SpeakerKind.PARTY))));
	}

	@Test
	void portableIsOmnidirectional() {
		assertTrue(RadiationSummary.of(RadiationPattern.of(Cabinets.of(SpeakerKind.PORTABLE))).omni());
		assertTrue(RadiationSummary.of(RadiationPattern.FLAT).omni());
	}
}
