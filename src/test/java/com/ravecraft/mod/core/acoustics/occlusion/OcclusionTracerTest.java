package com.ravecraft.mod.core.acoustics.occlusion;

import com.ravecraft.mod.core.acoustics.Vec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Objects;

import org.junit.jupiter.api.Test;

class OcclusionTracerTest {
	private static final OcclusionSettings S = OcclusionSettings.DEFAULT;
	private static final AcousticMaterial STONE = material("stone");
	private static final AcousticMedium WATER = Objects.requireNonNull(S.medium("water"));
	private static final AcousticMedium VACUUM = Objects.requireNonNull(S.medium("vacuum"));

	private static AcousticMaterial material(String name) {
		return S.materials().stream().filter(m -> m.name().equals(name)).findFirst().orElseThrow();
	}

	private static PathLoss trace(TestGrid grid, double x0, double y0, double z0, double x1, double y1, double z1) {
		PathLoss loss = new OcclusionTracer(grid, S).trace(new Vec(x0, y0, z0), new Vec(x1, y1, z1),
				new TraceBudget(10_000));
		assertNotNull(loss);
		return loss;
	}

	/** Listener at (0.5, 0.5, 0.5), source at (6.5, 0.5, 0.5). */
	private static PathLoss traceX(TestGrid grid) {
		return trace(grid, 0.5, 0.5, 0.5, 6.5, 0.5, 0.5);
	}

	@Test
	void emptyGridLosesNothing() {
		PathLoss loss = traceX(new TestGrid());
		assertEquals(0, loss.lossDb());
		assertEquals(0, loss.highLossDb());
		assertEquals(6, loss.length(), 1e-12);
		assertFalse(loss.mute());
	}

	@Test
	void oneStoneWall() {
		PathLoss loss = traceX(new TestGrid().wall(3, STONE));
		assertEquals(15, loss.lossDb(), 1e-9);
		assertEquals(30, loss.highLossDb(), 1e-9);
	}

	@Test
	void fiveWallsCappedAtMax() {
		TestGrid grid = new TestGrid();
		for (int x = 1; x <= 5; x++) {
			grid.wall(x, STONE);
		}
		PathLoss loss = traceX(grid);
		assertEquals(60, loss.lossDb(), 1e-9);
		assertEquals(60, loss.highLossDb(), 1e-9);
	}

	@Test
	void slabCountsHalfAlongY() {
		PathLoss loss = trace(new TestGrid().slab(0, 3, 0, STONE), 0.5, 0.5, 0.5, 0.5, 6.5, 0.5);
		assertEquals(7.5, loss.lossDb(), 1e-9);
	}

	@Test
	void diagonalThroughCornerGrazesOnly() {
		// From (0.5, 0.5) to (2.4, 2.6) the ray crosses y = 1 at t = 0.5 / 2.1, then x = 1 at t = 0.5 / 1.9:
		// voxel (0, 1, 0) is only clipped at its corner, between those two crossings.
		double x0 = 0.5, y0 = 0.5, x1 = 2.4, y1 = 2.6;
		double dx = x1 - x0, dy = y1 - y0;
		double tIn = (1 - y0) / dy;
		double tOut = (1 - x0) / dx;
		double clipped = (tOut - tIn) * Math.hypot(dx, dy);
		assertTrue(clipped > 0 && clipped < 0.2);
		PathLoss loss = trace(new TestGrid().solid(0, 1, 0, STONE), x0, y0, 0.5, x1, y1, 0.5);
		assertEquals(15 * clipped, loss.lossDb(), 1e-9);
		assertEquals(30 * clipped, loss.highLossDb(), 1e-9);
	}

	@Test
	void rayAlongGridLineCountsWallOnce() {
		PathLoss loss = trace(new TestGrid().wall(3, STONE), 0, 0.5, 0.5, 6, 0.5, 0.5);
		assertEquals(15, loss.lossDb(), 1e-9);
	}

	@Test
	void rayOnVoxelEdgeCountsWallOnce() {
		// Along y = 1, z = 1, the shared edge of four voxel rows: one wall, whichever row is walked.
		PathLoss loss = trace(new TestGrid().wall(3, STONE), 0.5, 1, 1, 6.5, 1, 1);
		assertEquals(15, loss.lossDb(), 1e-9);
	}

	@Test
	void endpointsInsideSolidAddNothing() {
		PathLoss loss = traceX(new TestGrid().solid(0, 0, 0, STONE).solid(6, 0, 0, STONE));
		assertEquals(0, loss.lossDb());
		assertEquals(0, loss.highLossDb());
	}

	@Test
	void airToWaterInterface() {
		PathLoss loss = traceX(new TestGrid().fluidBox(3, -8, -8, 20, 8, 8, WATER));
		assertEquals(19.8, loss.lossDb(), 0.3);
		assertEquals(9.9 + 0.02 * 3.5, loss.highLossDb(), 0.3);
		assertEquals(Interfaces.lossDb(S.air(), WATER, S.interfaceScale()), loss.lossDb(), 1e-9);
	}

	@Test
	void bothUnderwaterNoInterface() {
		PathLoss loss = traceX(new TestGrid(WATER));
		assertEquals(0, loss.lossDb());
		assertEquals(0.02 * 6, loss.highLossDb(), 1e-6);
	}

	@Test
	void airPathLossIsLeftToPropagation() {
		assertEquals(0, traceX(new TestGrid()).highLossDb());
	}

	@Test
	void waterloggedSlabIsMaterialAndMedium() {
		PathLoss loss = traceX(new TestGrid(WATER).slab(3, 0, 0, STONE).fluid(3, 0, 0, WATER));
		assertEquals(7.5, loss.lossDb(), 1e-9);
		assertEquals(15 + 0.02 * 6, loss.highLossDb(), 1e-6);
	}

	@Test
	void vacuumAnywhereIsMute() {
		assertTrue(traceX(new TestGrid().fluid(3, 0, 0, VACUUM)).mute());
		assertTrue(traceX(new TestGrid().fluid(0, 0, 0, VACUUM)).mute());
	}

	@Test
	void budgetStopsTrace() {
		TraceBudget budget = new TraceBudget(3);
		assertNull(new OcclusionTracer(new TestGrid(), S).trace(new Vec(0.5, 0.5, 0.5), new Vec(6.5, 0.5, 0.5),
				budget));
		assertTrue(budget.exhausted());
	}

	@Test
	void budgetCountsVisitedVoxels() {
		TraceBudget budget = new TraceBudget(100);
		assertNotNull(new OcclusionTracer(new TestGrid(), S).trace(new Vec(0.5, 0.5, 0.5), new Vec(6.5, 0.5, 0.5),
				budget));
		assertEquals(7, budget.used());
		assertFalse(budget.exhausted());
	}

	@Test
	void zeroLengthSegmentAddsNothing() {
		PathLoss loss = trace(new TestGrid().solid(0, 0, 0, STONE), 0.5, 0.5, 0.5, 0.5, 0.5, 0.5);
		assertEquals(0, loss.lossDb());
		assertEquals(0, loss.length());
	}

	@Test
	void reverseDirectionMatches() {
		TestGrid grid = new TestGrid().wall(3, STONE).slab(5, 0, 0, STONE);
		PathLoss forward = trace(grid, 0.5, 0.5, 0.5, 6.5, 0.5, 0.5);
		PathLoss backward = trace(grid, 6.5, 0.5, 0.5, 0.5, 0.5, 0.5);
		assertEquals(22.5, forward.lossDb(), 1e-9);
		assertEquals(forward.lossDb(), backward.lossDb(), 1e-9);
	}

	/** A lake: water in x in [-8, 20], y, z in [-8, 8], air around. */
	private static TestGrid lake() {
		return new TestGrid().fluidBox(-8, -8, -8, 20, 8, 8, WATER);
	}

	@Test
	void solidVoxelBetweenWaterKeepsWater() {
		PathLoss loss = traceX(lake().solid(3, 0, 0, STONE));
		assertEquals(15, loss.lossDb(), 1e-9);
		assertEquals(30 + 0.02 * 6, loss.highLossDb(), 1e-9);
	}

	@Test
	void speakerVoxelWithoutFluidUnderwater() {
		// A speaker that cannot be waterlogged is a solid voxel without fluid: it is in the water around it.
		PathLoss loss = trace(lake().solid(2, 0, 0, STONE), 0.5, 0.5, 0.5, 2.5, 0.5, 0.5);
		assertEquals(0, loss.lossDb(), 1e-9);
		assertEquals(0.02 * 2, loss.highLossDb(), 1e-9);
	}

	@Test
	void diverThroughOneStoneToSubmergedSpeaker() {
		PathLoss loss = traceX(lake().solid(3, 0, 0, STONE).solid(6, 0, 0, STONE));
		assertEquals(15, loss.lossDb(), 1e-9);
		assertEquals(30 + 0.02 * 6, loss.highLossDb(), 1e-9);
	}

	@Test
	void startVoxelWithoutMediumAdoptsTheFirstOne() {
		// The listener's voxel is solid: the walk takes the water it meets next, with no interface, and the water
		// absorbs over the whole segment.
		PathLoss loss = traceX(lake().solid(0, 0, 0, STONE));
		assertEquals(0, loss.lossDb(), 1e-9);
		assertEquals(0.02 * 6, loss.highLossDb(), 1e-9);
	}

	@Test
	void solidVoxelAtTheSurfaceKeepsTheMediumItCameFrom() {
		// Water up to x = 3, a solid at x = 4, air beyond: one interface, where the air begins.
		TestGrid grid = new TestGrid().fluidBox(-8, -8, -8, 4, 8, 8, WATER).solid(4, 0, 0, STONE);
		PathLoss loss = traceX(grid);
		assertEquals(15 + Interfaces.lossDb(WATER, S.air(), S.interfaceScale()), loss.lossDb(), 1e-9);
	}

	@Test
	void onlySolidVoxelsTakeTheDefaultMedium() {
		PathLoss inWater = trace(new TestGrid(WATER).solid(0, 0, 0, STONE).solid(1, 0, 0, STONE),
				0.5, 0.5, 0.5, 1.5, 0.5, 0.5);
		assertEquals(0, inWater.lossDb(), 1e-9);
		assertEquals(0.02, inWater.highLossDb(), 1e-9);
		assertTrue(trace(new TestGrid(VACUUM).solid(0, 0, 0, STONE).solid(1, 0, 0, STONE),
				0.5, 0.5, 0.5, 1.5, 0.5, 0.5).mute());
	}
}
