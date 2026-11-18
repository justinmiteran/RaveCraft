package com.ravecraft.mod.core.acoustics.reverb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import com.ravecraft.mod.core.acoustics.occlusion.AcousticMaterial;
import com.ravecraft.mod.core.acoustics.occlusion.AcousticMedium;
import com.ravecraft.mod.core.acoustics.occlusion.OcclusionSettings;
import com.ravecraft.mod.core.acoustics.occlusion.TestGrid;
import com.ravecraft.mod.core.acoustics.occlusion.TraceBudget;

class RoomProbeTest {
	private static final OcclusionSettings S = OcclusionSettings.DEFAULT;
	private static final AcousticMaterial STONE = material("stone");
	private static final AcousticMaterial WOOL = material("wool");
	private static final AcousticMedium WATER = Objects.requireNonNull(S.medium("water"));

	private static AcousticMaterial material(String name) {
		return S.materials().stream().filter(m -> m.name().equals(name)).findFirst().orElseThrow();
	}

	private static List<RayHit> measure(TestGrid grid, double x, double y, double z, int rays, double length) {
		RoomProbe probe = new RoomProbe(grid);
		probe.start(x, y, z, rays, length);
		assertTrue(probe.run(new TraceBudget(Integer.MAX_VALUE)));
		return probe.hits();
	}

	private static RoomShape shape(TestGrid grid, double x, double y, double z) {
		return RoomShape.of(measure(grid, x, y, z, 32, 64), 64);
	}

	@Test
	void closedStoneRoomIsClosedAndLive() {
		RoomShape shape = shape(new TestGrid().shell(-5, -3, -5, 5, 3, 5, STONE), 0.5, 0.5, 0.5);
		assertEquals(0, shape.openness());
		assertTrue(shape.meanFreePath() >= 3 && shape.meanFreePath() <= 6, "mfp " + shape.meanFreePath());
		assertEquals(6, shape.rt60(6));
	}

	@Test
	void woolRoomDecaysFast() {
		RoomShape wool = shape(new TestGrid().shell(-5, -3, -5, 5, 3, 5, WOOL), 0.5, 0.5, 0.5);
		RoomShape stone = shape(new TestGrid().shell(-5, -3, -5, 5, 3, 5, STONE), 0.5, 0.5, 0.5);
		assertTrue(wool.rt60(6) < 0.3, "wool " + wool.rt60(6));
		assertTrue(wool.rt60(6) < stone.rt60(6));
	}

	@Test
	void openFieldIsOpen() {
		RoomShape shape = shape(new TestGrid().fill(-40, -1, -40, 40, -1, 40, STONE), 0.5, 0.5, 0.5);
		assertTrue(shape.openness() > 0.4, "openness " + shape.openness());
	}

	@Test
	void waterSurfaceStopsARay() {
		TestGrid under = new TestGrid().fluidBox(-20, -20, -20, 20, 5, 20, WATER);
		List<RayHit> hits = measure(under, 0.5, 0.5, 0.5, 200, 64);
		assertTrue(hits.stream().anyMatch(h -> !h.open() && h.absorption() == 1 && h.distance() > 4 && h.distance() < 6));

		TestGrid above = new TestGrid().fluidBox(-20, -20, -20, 20, 0, 20, WATER);
		List<RayHit> down = measure(above, 0.5, 5.5, 0.5, 200, 64);
		assertTrue(down.stream().anyMatch(h -> !h.open() && h.absorption() == 0.01 && h.distance() > 4 && h.distance() < 6));
	}

	@Test
	void startingInsideASolidStillMeasures() {
		TestGrid air = new TestGrid().shell(-5, -3, -5, 5, 3, 5, STONE);
		TestGrid solid = new TestGrid().shell(-5, -3, -5, 5, 3, 5, STONE).solid(0, 0, 0, STONE);
		RoomShape a = shape(air, 0.5, 0.5, 0.5);
		RoomShape b = shape(solid, 0.5, 0.5, 0.5);
		assertEquals(a.openness(), b.openness());
		assertEquals(a.meanFreePath(), b.meanFreePath(), 1);
	}

	@Test
	void resumedProbeEqualsOneShot() {
		TestGrid grid = new TestGrid().shell(-5, -3, -5, 5, 3, 5, STONE).fluidBox(-2, -2, -2, 1, 1, 1, WATER);
		List<RayHit> whole = measure(grid, 0.5, 0.5, 0.5, 32, 64);
		RoomProbe probe = new RoomProbe(grid);
		probe.start(0.5, 0.5, 0.5, 32, 64);
		int calls = 0;
		while (!probe.run(new TraceBudget(50))) {
			calls++;
			assertTrue(calls < 10_000);
		}
		assertTrue(calls > 1);
		assertNotNull(probe.hits());
		assertEquals(whole, probe.hits());
	}
}
