package com.ravecraft.mod.core.acoustics.reverb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ravecraft.mod.core.acoustics.occlusion.AcousticMaterial;
import com.ravecraft.mod.core.acoustics.occlusion.OcclusionSettings;
import com.ravecraft.mod.core.acoustics.occlusion.TestGrid;
import com.ravecraft.mod.core.acoustics.occlusion.TraceBudget;

class ReverbParamsTest {
	private static final OcclusionSettings S = OcclusionSettings.DEFAULT;
	private static final ReverbSettings R = ReverbSettings.DEFAULT;

	private static AcousticMaterial material(String name) {
		return S.materials().stream().filter(m -> m.name().equals(name)).findFirst().orElseThrow();
	}

	private static RoomShape room(AcousticMaterial m) {
		RoomProbe probe = new RoomProbe(new TestGrid().shell(-5, -3, -5, 5, 3, 5, m));
		probe.start(0.5, 0.5, 0.5, 32, 64);
		assertTrue(probe.run(new TraceBudget(Integer.MAX_VALUE)));
		return RoomShape.of(probe.hits(), 64);
	}

	private static void assertInBounds(ReverbParams p) {
		assertTrue(p.decayTime() >= 0.1 && p.decayTime() <= 20, "decay " + p);
		assertTrue(p.decayHfRatio() >= 0.1 && p.decayHfRatio() <= 2, "hf " + p);
		assertTrue(p.gain() >= 0 && p.gain() <= 1, "gain " + p);
		assertTrue(p.reflectionsGain() >= 0 && p.reflectionsGain() <= 3.16, "refl " + p);
		assertTrue(p.reflectionsDelay() >= 0 && p.reflectionsDelay() <= 0.3, "rdelay " + p);
		assertTrue(p.lateReverbDelay() >= 0 && p.lateReverbDelay() <= 0.1, "ldelay " + p);
		assertTrue(p.density() >= 0 && p.density() <= 1, "density " + p);
		assertTrue(p.diffusion() >= 0 && p.diffusion() <= 1, "diffusion " + p);
		assertTrue(p.airAbsorptionGainHf() >= 0.892 && p.airAbsorptionGainHf() <= 1, "air " + p);
	}

	@Test
	void stoneRoomIsLiveAndWoolRoomIsDull() {
		ReverbParams stone = ReverbParams.of(room(material("stone")), R);
		ReverbParams wool = ReverbParams.of(room(material("wool")), R);
		assertTrue(stone.decayTime() > wool.decayTime());
		assertTrue(stone.decayHfRatio() > wool.decayHfRatio());
	}

	@Test
	void openAirIsDry() {
		ReverbParams p = ReverbParams.of(new RoomShape(1, 64, 1, 1), R);
		assertEquals(0, p.gain());
		assertEquals(0, p.reflectionsGain());
	}

	@Test
	void smallRoomIsDenserThanBigRoom() {
		ReverbParams small = ReverbParams.of(new RoomShape(0, 2, 0.2, 0.2), R);
		ReverbParams big = ReverbParams.of(new RoomShape(0, 40, 0.2, 0.2), R);
		assertTrue(small.density() > big.density());
	}

	@Test
	void extremeSettingsStayInEaxBounds() {
		ReverbSettings extreme = new ReverbSettings(true, 128, 20, 1, 6, EchoSettings.DEFAULT, List.of());
		for (double l : new double[] { 0, 0.5, 128 }) {
			for (double openness : new double[] { 0, 1 }) {
				for (double a : new double[] { 0, 0.5, 1 }) {
					assertInBounds(ReverbParams.of(new RoomShape(openness, l, a, a), extreme));
				}
			}
		}
		assertInBounds(ReverbParams.SILENT);
		assertInBounds(ReverbParams.of(FluidReverb.WATER));
		assertInBounds(ReverbParams.of(FluidReverb.LAVA));
	}

	@Test
	void waterPresetUsesItsValues() {
		ReverbParams p = ReverbParams.of(FluidReverb.WATER);
		assertEquals(1.2, p.decayTime());
		assertEquals(0.3, p.decayHfRatio());
		assertEquals(0.4, p.gain());
		assertEquals(0.2, p.reflectionsGain());
		assertEquals(1, p.density());
		assertEquals(1, p.diffusion());
	}

	@Test
	void towardInterpolatesAndClamps() {
		ReverbParams a = ReverbParams.SILENT;
		ReverbParams b = ReverbParams.of(FluidReverb.WATER);
		ReverbParams mid = a.toward(b, 0.5);
		assertEquals((a.decayTime() + b.decayTime()) / 2, mid.decayTime(), 1e-9);
		assertEquals(b.gain() / 2, mid.gain(), 1e-9);
		assertEquals(b, a.toward(b, 2));
		assertEquals(a, a.toward(b, -1));
	}
}
