package com.ravecraft.mod.core.acoustics.reverb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReverbSettingsTest {
	private static ReverbSettings with(double rayLength, double decay, double wet, double loss, EchoSettings echo,
			List<FluidReverb> media) {
		return new ReverbSettings(true, rayLength, decay, wet, loss, echo, media);
	}

	@Test
	void defaultsMatchSpec() {
		ReverbSettings s = ReverbSettings.DEFAULT;
		assertTrue(s.enabled());
		assertEquals(64, s.rayLength());
		assertEquals(6, s.maxDecaySeconds());
		assertEquals(0.32, s.maxWetGain());
		assertEquals(6, s.sameSpaceLossDb());
		assertEquals(new EchoSettings(17, 35, 4, 0.2, 0.6, 0.15, 0.5), s.echo());
		assertEquals(List.of(new FluidReverb("water", 1.2, 1, 1, 0.4, 0.3, true, 0.4, 1.5),
				new FluidReverb("lava", 0.6, 1, 1, 0.3, 0.2, false, 0.25, 2)), s.media());
		assertEquals(16, ReverbSettings.MAX_MEDIA);
	}

	@Test
	void valuesAreBounded() {
		List<String> warnings = new ArrayList<>();
		EchoSettings wildEcho = new EchoSettings(5, 99, 40, 2, 3, 0.9, 1);
		FluidReverb wildFluid = new FluidReverb("water", 99, 2, -1, 5, 9, true, 50, 99);
		ReverbSettings c = with(500, 0.1, 4, 99, wildEcho, List.of(wildFluid)).clamped(warnings::add);
		assertEquals(128, c.rayLength());
		assertEquals(0.5, c.maxDecaySeconds());
		assertEquals(1, c.maxWetGain());
		assertEquals(60, c.sameSpaceLossDb());
		assertEquals(new EchoSettings(10, 35, 16, 1, 1, 0.5, 0.99), c.echo());
		assertEquals(new FluidReverb("water", 20, 1, 0, 1, 2, true, 5, 10), c.media().get(0));
		for (String key : List.of("reverb.rayLength", "reverb.maxDecaySeconds", "reverb.maxWetGain",
				"reverb.sameSpaceLossDb", "reverb.echo.minDistance", "reverb.echo.maxDistance", "reverb.echo.minRays",
				"reverb.echo.maxAbsorption", "reverb.echo.gain", "reverb.echo.feedback", "reverb.echo.damping",
				"reverb.media.water.decaySeconds", "reverb.media.water.density", "reverb.media.water.diffusion",
				"reverb.media.water.gain", "reverb.media.water.decayHfRatio", "reverb.media.water.chorusRateHz",
				"reverb.media.water.chorusDepthMs")) {
			assertTrue(warnings.stream().anyMatch(w -> w.contains(key + " ")), "no warning for " + key);
		}
	}

	@Test
	void maxDistanceNeverBelowMinDistance() {
		EchoSettings d = EchoSettings.DEFAULT;
		ReverbSettings s = with(64, 6, 0.32, 6,
				new EchoSettings(30, 20, d.minRays(), d.maxAbsorption(), d.gain(), d.feedback(), d.damping()),
				ReverbSettings.DEFAULT.media());
		assertEquals(30, s.clamped(w -> { }).echo().maxDistance());
	}

	@Test
	void invalidAndDuplicateFluidsAreDropped() {
		List<String> warnings = new ArrayList<>();
		FluidReverb bad = new FluidReverb("Bad Name", 1, 1, 1, 0.4, 0.3, false, 0.4, 1.5);
		FluidReverb dup = new FluidReverb("water", 2, 1, 1, 0.4, 0.3, false, 0.4, 1.5);
		ReverbSettings c = with(64, 6, 0.32, 6, EchoSettings.DEFAULT, List.of(FluidReverb.WATER, bad, dup))
				.clamped(warnings::add);
		assertEquals(List.of(FluidReverb.WATER), c.media());
		assertEquals(2, warnings.size());
	}

	@Test
	void fluidLooksUpByMedium() {
		assertNotNull(ReverbSettings.DEFAULT.fluid("water"));
		assertNull(ReverbSettings.DEFAULT.fluid("slime"));
	}
}
