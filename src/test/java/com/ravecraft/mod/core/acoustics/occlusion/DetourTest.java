package com.ravecraft.mod.core.acoustics.occlusion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Objects;

import org.junit.jupiter.api.Test;

class DetourTest {
	private static final OcclusionSettings S = OcclusionSettings.DEFAULT;
	private static final AcousticMaterial STONE = S.materials().stream()
			.filter(m -> m.name().equals("stone")).findFirst().orElseThrow();
	private static final AcousticMedium VACUUM = Objects.requireNonNull(S.medium("vacuum"));
	private static final double ROLLOFF = 1;
	private static final double RANGE = 64;

	private static OcclusionSettings settings(boolean enabled, boolean detours) {
		return new OcclusionSettings(enabled, S.maxLossDb(), S.media(), S.interfaceScale(), S.interfaceHighRatio(),
				S.dimensions(), S.materials(), detours, S.bendLossDb(), S.bendHighLossDb());
	}

	/** A stone shell around the open box x, z in [0, 6], y in [0, 4]. */
	private static TestGrid room() {
		return new TestGrid().shell(0, 0, 0, 6, 4, 6, STONE);
	}

	/** {@link #room()} with a 1x2 opening in the wall x = 7 at z = 1, y = 0..1. */
	private static TestGrid openDoor() {
		return room().clear(7, 0, 1, 7, 1, 1);
	}

	/** Listener outside the room at (12.5, 1.5, 3.5), source inside at (3.5, 1.5, 3.5). */
	private static Occlusion roomOcclusion(TestGrid grid, OcclusionSettings settings, double range) {
		Occlusion o = new OcclusionTracer(grid, settings)
				.occlusion(12.5, 1.5, 3.5, 3.5, 1.5, 3.5, ROLLOFF, range, new TraceBudget(100_000));
		assertNotNull(o);
		return o;
	}

	@Test
	void closedRoomIsDirectLoss() {
		Occlusion o = roomOcclusion(room(), S, RANGE);
		assertEquals(15, o.lossDb(), 1e-9);
		assertEquals(15, o.highLossDb(), 1e-9);
	}

	@Test
	void openDoorLetsSoundAround() {
		Occlusion o = roomOcclusion(openDoor(), S, RANGE);
		assertTrue(o.lossDb() < 10, "lossDb " + o.lossDb());
		assertTrue(o.highLossDb() > 0, "highLossDb " + o.highLossDb());
	}

	@Test
	void twoOpeningsLouderThanOne() {
		Occlusion one = roomOcclusion(openDoor(), S, RANGE);
		Occlusion two = roomOcclusion(openDoor().clear(7, 0, 5, 7, 1, 5), S, RANGE);
		assertTrue(two.lossDb() < one.lossDb(), one.lossDb() + " -> " + two.lossDb());
	}

	/**
	 * Single-layer tunnels at y = 1 cut in solid stone: room A (x, z in [0, 4], source at its centre), a corridor
	 * along z = 2 to a 3x3 junction (x in [8, 10], z in [1, 3]), a corridor along x = 9 up to room B (x in [7, 11],
	 * z in [8, 12], listener at (9.5, 1.5, 10.5)).
	 */
	@Test
	void lCorridorNeedsTwoBends() {
		TestGrid grid = new TestGrid().fill(-3, -1, -3, 14, 3, 15, STONE)
				.clear(0, 1, 0, 4, 1, 4)
				.clear(5, 1, 2, 7, 1, 2)
				.clear(8, 1, 1, 10, 1, 3)
				.clear(9, 1, 4, 9, 1, 7)
				.clear(7, 1, 8, 11, 1, 12);
		double[] listener = {9.5, 1.5, 10.5};
		double[] source = {2.5, 1.5, 2.5};
		assertNoSingleWaypoint(grid, listener, source);
		assertNoSingleWaypoint(grid, source, listener);

		OcclusionTracer tracer = new OcclusionTracer(grid, S);
		PathLoss direct = tracer.trace(listener[0], listener[1], listener[2], source[0], source[1], source[2],
				new TraceBudget(100_000));
		assertNotNull(direct);
		Occlusion o = tracer.occlusion(listener[0], listener[1], listener[2], source[0], source[1], source[2],
				ROLLOFF, RANGE, new TraceBudget(100_000));
		assertNotNull(o);
		assertTrue(o.lossDb() <= direct.lossDb() - 8, direct.lossDb() + " -> " + o.lossDb());
	}

	/** No candidate waypoint around {@code anchor} (26 directions, 3 and 6 blocks) sees both ends with < 3 dB. */
	private static void assertNoSingleWaypoint(TestGrid grid, double[] anchor, double[] other) {
		OcclusionTracer tracer = new OcclusionTracer(grid, S);
		VoxelSample sample = new VoxelSample();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dy == 0 && dz == 0) {
						continue;
					}
					double n = Math.sqrt(dx * dx + dy * dy + dz * dz);
					for (double r : new double[] {3, 6}) {
						double px = Math.floor(anchor[0]) + 0.5 + dx / n * r;
						double py = Math.floor(anchor[1]) + 0.5 + dy / n * r;
						double pz = Math.floor(anchor[2]) + 0.5 + dz / n * r;
						grid.sample((int) Math.floor(px), (int) Math.floor(py), (int) Math.floor(pz), sample);
						if (sample.material != null) {
							continue;
						}
						PathLoss near = tracer.trace(anchor[0], anchor[1], anchor[2], px, py, pz,
								new TraceBudget(100_000));
						PathLoss far = tracer.trace(px, py, pz, other[0], other[1], other[2],
								new TraceBudget(100_000));
						assertNotNull(near);
						assertNotNull(far);
						assertTrue(near.lossDb() >= 3 || far.lossDb() >= 3,
								"waypoint (" + px + ", " + py + ", " + pz + ") sees both ends");
					}
				}
			}
		}
	}

	@Test
	void detourLongerThanRangeIgnored() {
		Occlusion o = roomOcclusion(openDoor(), S, 9.5);
		assertEquals(15, o.lossDb(), 1e-9);
		assertEquals(15, o.highLossDb(), 1e-9);
	}

	@Test
	void clearPathTriesNoDetour() {
		TestGrid grid = new TestGrid();
		TraceBudget direct = new TraceBudget(100_000);
		assertNotNull(new OcclusionTracer(grid, S).trace(0.5, 0.5, 0.5, 6.5, 0.5, 0.5, direct));
		TraceBudget budget = new TraceBudget(100_000);
		Occlusion o = new OcclusionTracer(grid, S).occlusion(0.5, 0.5, 0.5, 6.5, 0.5, 0.5, ROLLOFF, RANGE, budget);
		assertEquals(Occlusion.NONE, o);
		assertEquals(direct.used(), budget.used());
		assertEquals(7, budget.used());
	}

	@Test
	void detoursDisabledUsesDirectOnly() {
		Occlusion o = roomOcclusion(openDoor(), settings(true, false), RANGE);
		assertEquals(15, o.lossDb(), 1e-9);
		assertEquals(15, o.highLossDb(), 1e-9);
	}

	@Test
	void disabledVisitsNothing() {
		TraceBudget budget = new TraceBudget(100_000);
		Occlusion o = new OcclusionTracer(openDoor(), settings(false, true))
				.occlusion(12.5, 1.5, 3.5, 3.5, 1.5, 3.5, ROLLOFF, RANGE, budget);
		assertSame(Occlusion.NONE, o);
		assertEquals(0, budget.used());
	}

	@Test
	void mutedListenerStaysMute() {
		Occlusion o = roomOcclusion(openDoor().fluid(12, 1, 3, VACUUM), S, RANGE);
		assertEquals(Occlusion.MUTE, o);
	}

	@Test
	void budgetExhaustedReturnsNull() {
		assertNull(new OcclusionTracer(openDoor(), S)
				.occlusion(12.5, 1.5, 3.5, 3.5, 1.5, 3.5, ROLLOFF, RANGE, new TraceBudget(20)));
	}
}
