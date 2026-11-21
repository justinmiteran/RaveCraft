package com.ravecraft.mod.core.acoustics.occlusion;

import com.ravecraft.mod.core.acoustics.Vec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;

class PathCaptureTest {
	private static final OcclusionSettings S = OcclusionSettings.DEFAULT;
	private static final AcousticMaterial STONE = material("stone");
	private static final AcousticMedium WATER = Objects.requireNonNull(S.medium("water"));
	private static final AcousticMedium VACUUM = Objects.requireNonNull(S.medium("vacuum"));
	private static final double ROLLOFF = 1;
	private static final double RANGE = 64;

	private static AcousticMaterial material(String name) {
		return S.materials().stream().filter(m -> m.name().equals(name)).findFirst().orElseThrow();
	}

	private static OcclusionSettings settings(boolean enabled) {
		return new OcclusionSettings(enabled, S.maxLossDb(), S.media(), S.interfaceScale(), S.interfaceHighRatio(),
				S.dimensions(), S.materials(), S.detours(), S.bendLossDb(), S.bendHighLossDb());
	}

	/** Listener at (0.5, 0.5, 0.5), source at (x + 0.5, 0.5, 0.5). */
	private static Occlusion alongX(TestGrid grid, OcclusionSettings settings, int x, PathCapture capture) {
		Occlusion o = new OcclusionTracer(grid, settings).occlusion(new Vec(0.5, 0.5, 0.5), new Vec(x + 0.5, 0.5,
				0.5), ROLLOFF, RANGE,
				new TraceBudget(1_000_000), capture);
		assertNotNull(o);
		return o;
	}

	private static void assertStep(PathCapture.Step step, int x, String name, double lossDb) {
		assertEquals(x, step.x());
		assertEquals(0, step.y());
		assertEquals(0, step.z());
		assertEquals(name, step.name());
		assertEquals(lossDb, step.lossDb(), 1e-9);
	}

	@Test
	void stoneWallIsOneStep() {
		PathCapture capture = new PathCapture();
		alongX(new TestGrid().wall(3, STONE), S, 6, capture);
		assertEquals(1, capture.steps().size());
		assertStep(capture.steps().getFirst(), 3, "stone", 15);
		assertEquals(15, Objects.requireNonNull(capture.direct()).lossDb(), 1e-9);
		assertEquals(0, capture.truncated());
	}

	@Test
	void endsAreNotSteps() {
		PathCapture capture = new PathCapture();
		alongX(new TestGrid().solid(0, 0, 0, STONE).solid(6, 0, 0, STONE), S, 6, capture);
		assertTrue(capture.steps().isEmpty());
	}

	@Test
	void waterGivesInterfaces() {
		PathCapture capture = new PathCapture();
		alongX(new TestGrid().fluidBox(TestGrid.box(3, -8, -8, 5, 8, 8), WATER), S, 8, capture);
		List<String> names = capture.steps().stream().map(PathCapture.Step::name).toList();
		assertEquals(List.of("air→water", "water→air"), names);
		assertEquals(3, capture.steps().get(0).x());
		assertEquals(6, capture.steps().get(1).x());
		double interfaceLoss = Interfaces.lossDb(S.air(), WATER, S.interfaceScale());
		assertEquals(interfaceLoss, capture.steps().get(0).lossDb(), 1e-9);
		double sum = capture.steps().stream().mapToDouble(PathCapture.Step::lossDb).sum();
		assertEquals(Objects.requireNonNull(capture.direct()).lossDb(), sum, 1e-9);
	}

	@Test
	void truncatesAt64() {
		TestGrid grid = new TestGrid();
		for (int i = 0; i < 70; i++) {
			grid.solid(2 + 2 * i, 0, 0, STONE);
		}
		PathCapture capture = new PathCapture();
		new OcclusionTracer(grid, S).occlusion(new Vec(0.5, 0.5, 0.5), new Vec(145.5, 0.5, 0.5), ROLLOFF, 1000,
				new TraceBudget(1_000_000), capture);
		assertEquals(PathCapture.MAX_STEPS, capture.steps().size());
		assertEquals(6, capture.truncated());
		assertEquals(2, capture.steps().get(0).x());
	}

	/** The room of {@code DetourTest}: a stone shell around x, z in [0, 6], y in [0, 4], door at x = 7, z = 1. */
	private static TestGrid openDoor() {
		return new TestGrid().shell(TestGrid.box(0, 0, 0, 6, 4, 6), STONE).clear(TestGrid.box(7, 0, 1, 7, 1, 1));
	}

	@Test
	void detoursAreTheSummedOnes() {
		PathCapture capture = new PathCapture();
		Occlusion with = new OcclusionTracer(openDoor(), S).occlusion(new Vec(12.5, 1.5, 3.5), new Vec(3.5, 1.5, 3.5),
				ROLLOFF, RANGE,
				new TraceBudget(100_000), capture);
		Occlusion without = new OcclusionTracer(openDoor(), S).occlusion(new Vec(12.5, 1.5, 3.5), new Vec(3.5, 1.5,
				3.5), ROLLOFF,
				RANGE, new TraceBudget(100_000));
		assertEquals(without, with);
		double direct = Objects.requireNonNull(capture.direct()).lossDb();
		assertFalse(capture.detours().isEmpty());
		assertTrue(capture.detours().size() <= 3);
		for (PathCapture.CapturedDetour detour : capture.detours()) {
			double[] first = detour.points().getFirst();
			double[] last = detour.points().getLast();
			assertEquals(3.5, first[0], 1e-9);
			assertEquals(12.5, last[0], 1e-9);
			assertTrue(detour.points().size() >= 3);
			assertTrue(detour.lossDb() < direct);
		}
	}

	@Test
	void disabledResetsCapture() {
		PathCapture capture = new PathCapture();
		alongX(new TestGrid().wall(3, STONE), S, 6, capture);
		assertFalse(capture.steps().isEmpty());
		alongX(new TestGrid().wall(3, STONE), settings(false), 6, capture);
		assertTrue(capture.steps().isEmpty());
		assertTrue(capture.detours().isEmpty());
		assertNull(capture.direct());
	}

	@Test
	void vacuumKeepsStepsUntilMute() {
		PathCapture capture = new PathCapture();
		alongX(new TestGrid().solid(2, 0, 0, STONE).fluid(4, 0, 0, VACUUM), S, 6, capture);
		assertEquals(2, capture.steps().size());
		assertStep(capture.steps().getFirst(), 2, "stone", 15);
		PathCapture.Step mute = capture.steps().get(1);
		assertEquals(4, mute.x());
		assertEquals("vacuum", mute.name());
		assertEquals(Double.POSITIVE_INFINITY, mute.lossDb());
		assertTrue(Objects.requireNonNull(capture.direct()).mute());
	}
}
