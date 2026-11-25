package com.ravecraft.mod.core.acoustics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ravecraft.mod.core.acoustics.radiation.BandGains;
import com.ravecraft.mod.core.acoustics.radiation.Cabinet;
import com.ravecraft.mod.core.acoustics.radiation.Cabinets;
import com.ravecraft.mod.core.acoustics.radiation.Driver;
import com.ravecraft.mod.core.acoustics.radiation.RadiationPattern;
import com.ravecraft.mod.core.speaker.CarryPlace;
import com.ravecraft.mod.core.speaker.SpeakerKind;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpeakerAxisTest {
	private static final double DELTA = 1e-9;
	private static final RadiationPattern PARTY = RadiationPattern.of(Cabinets.of(SpeakerKind.PARTY));
	/** A driver turned to the speaker's right, then one turned up. */
	private static final RadiationPattern RIGHT = driver(90, 0);
	private static final RadiationPattern UP = driver(0, 90);

	@Test
	void blockFollowsFacing() {
		SpeakerAxis axis = SpeakerAxis.block(1, 0);
		assertEquals(1, axis.x(), DELTA);
		assertEquals(0, axis.z(), DELTA);
		assertTrue(axis.directed());
	}

	@Test
	void handFollowsLook() {
		SpeakerAxis axis = SpeakerAxis.carried(CarryPlace.HAND, 90, 0);
		assertEquals(-1, axis.x(), DELTA);
		assertEquals(0, axis.z(), DELTA);
		assertTrue(axis.directed());
	}

	@Test
	void inventoryFollowsLookNotBody() {
		SpeakerAxis axis = SpeakerAxis.carried(CarryPlace.INVENTORY, 90, 180);
		assertEquals(-1, axis.x(), DELTA);
		assertEquals(0, axis.z(), DELTA);
	}

	@Test
	void backPointsBehind() {
		SpeakerAxis axis = SpeakerAxis.carried(CarryPlace.BACK, 45, 0);
		assertEquals(0, axis.x(), DELTA);
		assertEquals(-1, axis.z(), DELTA);
		assertTrue(axis.directed());
	}

	@Test
	void groundIsOmni() {
		assertEquals(Directivity.OMNI, SpeakerAxis.GROUND.directivity(DeviceProfile.PARTY));
		assertFalse(SpeakerAxis.GROUND.directed());
	}

	@Test
	void directedKeepsProfile() {
		assertEquals(DeviceProfile.PARTY.directivity(), SpeakerAxis.block(0, 1).directivity(DeviceProfile.PARTY));
	}

	@Test
	void hearsTheFrontAndTheBack() {
		SpeakerAxis east = SpeakerAxis.block(1, 0);
		assertEquals(PARTY.at(0, 0).high(), east.heard(PARTY, 10, 0, 0).high(), DELTA);
		assertEquals(PARTY.at(180, 0).high(), east.heard(PARTY, -10, 0, 0).high(), DELTA);
	}

	@Test
	void rightOfASpeakerFacingNorthIsEast() {
		SpeakerAxis north = SpeakerAxis.block(0, -1);
		assertEquals(RIGHT.at(90, 0).high(), north.heard(RIGHT, 10, 0, 0).high(), DELTA);
		assertEquals(RIGHT.at(-90, 0).high(), north.heard(RIGHT, -10, 0, 0).high(), DELTA);
	}

	@Test
	void hearsFromAbove() {
		assertEquals(UP.at(0, 45).high(), SpeakerAxis.block(0, 1).heard(UP, 0, 5, 5).high(), DELTA);
	}

	@Test
	void onTheBackTheSoundGoesBehindTheCarrier() {
		SpeakerAxis back = SpeakerAxis.carried(CarryPlace.BACK, 0, 0);
		assertEquals(1, back.heard(PARTY, 0, 0, -10).high(), DELTA);
	}

	@Test
	void onTheGroundEveryDirectionIsTheSame() {
		assertEquals(PARTY.around(), SpeakerAxis.GROUND.heard(PARTY, 0, 0, 10));
		assertEquals(PARTY.around(), SpeakerAxis.GROUND.heard(PARTY, 10, 3, 0));
	}

	@Test
	void earAtTheSpeakerHearsItWhole() {
		assertEquals(BandGains.FLAT, SpeakerAxis.block(1, 0).heard(PARTY, 0, 0, 0));
		assertEquals(BandGains.FLAT, SpeakerAxis.GROUND.heard(PARTY, 0, 0, 0));
	}

	@Test
	void straightAboveIsFinite() {
		assertEquals(PARTY.at(0, 90), SpeakerAxis.block(1, 0).heard(PARTY, 0, 4, 0));
		assertEquals(PARTY.at(0, -90), SpeakerAxis.block(1, 0).heard(PARTY, 0, -4, 0));
	}

	private static RadiationPattern driver(double azimuth, double elevation) {
		return RadiationPattern.of(new Cabinet(0.75, 1, 0.62,
				List.of(new Driver(azimuth, elevation, 0, 0, 0, 0.13, 0, 0))));
	}
}
