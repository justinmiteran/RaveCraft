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
import com.ravecraft.mod.core.acoustics.occlusion.VoxelSample;

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
	@Test
	void hitsRecordTheFaceTheyMet() {
		List<RayHit> hits = measure(new TestGrid().wall(5, STONE).wall(-5, STONE), 0.5, 0.5, 0.5, 32, 64);
		List<RayHit> closed = hits.stream().filter(h -> !h.open()).toList();
		assertTrue(closed.size() > 4);
		for (RayHit h : closed) {
			assertEquals(VoxelSample.X, h.faceAxis());
			assertTrue(h.facePlane() == 5 || h.facePlane() == -4, "plane " + h.facePlane());
		}
		assertTrue(hits.stream().filter(RayHit::open).allMatch(h -> h.faceAxis() == RayHit.NO_FACE));
	}

	@Test
	void smoothBoxIsRegular() {
		RoomShape shape = shape(new TestGrid().shell(-5, -3, -5, 5, 3, 5, STONE), 0.5, 0.5, 0.5);
		assertEquals(0, shape.irregularity());
	}

	@Test
	void ruggedCaveIsIrregular() {
		TestGrid cave = new TestGrid().shell(-5, -3, -5, 5, 3, 5, STONE);
		for (int a = -4; a <= 4; a++) {
			for (int b = -4; b <= 4; b++) {
				int d = Math.floorMod(a * 7 + b * 13, 3);
				if (d > 0) {
					cave.fill(a, -2, b, a, -3 + d, b, STONE);
					cave.fill(a, 3 - d, b, a, 2, b, STONE);
					cave.fill(-4, Math.clamp(a, -2, 2), b, -5 + d, Math.clamp(a, -2, 2), b, STONE);
					cave.fill(5 - d, Math.clamp(a, -2, 2), b, 4, Math.clamp(a, -2, 2), b, STONE);
				}
			}
		}
		cave.clear(-1, -1, -1, 1, 1, 1);
		RoomShape shape = RoomShape.of(measure(cave, 0.5, 0.5, 0.5, 48, 64), 64);
		assertTrue(shape.irregularity() > 0.4, "irregularity " + shape.irregularity());
	}

	@Test
	void tooFewSurfacesGiveANeutralIrregularity() {
		RoomShape open = RoomShape.of(List.of(new RayHit(true, 64, 1)), 64);
		assertEquals(0.5, open.irregularity());
		RoomShape few = RoomShape.of(List.of(new RayHit(false, 3, 0.1, VoxelSample.X, 3),
				new RayHit(false, 3, 0.1, VoxelSample.Y, 3), new RayHit(false, 3, 0.1, VoxelSample.Z, 3)), 64);
		assertEquals(0.5, few.irregularity());
	}

	@Test
	void wallDistanceIsPerpendicularToTheFace() {
		List<RayHit> hits = measure(new TestGrid().fill(20, -30, -30, 20, 30, 30, STONE), 0.5, 0.5, 0.5, 32, 64);
		List<RayHit> wall = hits.stream().filter(h -> !h.open()).toList();
		assertTrue(wall.size() > 3, "wall hits " + wall.size());
		for (RayHit hit : wall) {
			assertEquals(VoxelSample.X, hit.faceAxis());
			assertEquals(19.5, hit.wallDistance(), 1e-9);
			assertTrue(hit.distance() >= hit.wallDistance() - 1e-9);
		}
	}
}
