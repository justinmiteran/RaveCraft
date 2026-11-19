package com.ravecraft.mod.core.acoustics.debug.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import com.ravecraft.mod.core.acoustics.occlusion.AcousticMaterial;
import com.ravecraft.mod.core.acoustics.occlusion.Occlusion;
import com.ravecraft.mod.core.acoustics.occlusion.OcclusionSettings;
import com.ravecraft.mod.core.acoustics.occlusion.OcclusionTracer;
import com.ravecraft.mod.core.acoustics.occlusion.PathCapture;
import com.ravecraft.mod.core.acoustics.occlusion.TestGrid;
import com.ravecraft.mod.core.acoustics.occlusion.TraceBudget;

class PathViewTest {
	private static final OcclusionSettings S = OcclusionSettings.DEFAULT;
	private static final Vec LISTENER = new Vec(0.5, 0.5, 0.5);
	private static final Vec SOURCE = new Vec(6.5, 0.5, 0.5);

	private static AcousticMaterial material(String name) {
		return S.materials().stream().filter(m -> m.name().equals(name)).findFirst().orElseThrow();
	}

	private static Occlusion trace(TestGrid grid, PathCapture capture) {
		return Objects.requireNonNull(new OcclusionTracer(grid, S).occlusion(LISTENER.x(), LISTENER.y(), LISTENER.z(),
				SOURCE.x(), SOURCE.y(), SOURCE.z(), 1, 64, new TraceBudget(1_000_000), capture));
	}

	private static TestGrid stoneThenWood() {
		return new TestGrid().solid(2, 0, 0, material("stone")).solid(4, 0, 0, material("wood"));
	}

	@Test
	void soundOrder() {
		PathCapture capture = new PathCapture();
		Occlusion occlusion = trace(stoneThenWood(), capture);
		PathView view = PathView.of(7, SOURCE, LISTENER, capture, occlusion);
		assertEquals(2, view.crossings().size());
		Crossing wood = view.crossings().get(0);
		Crossing stone = view.crossings().get(1);
		assertEquals(4, wood.x());
		assertEquals("wood", wood.name());
		assertEquals(8, wood.lossDb(), 1e-9);
		assertEquals(8, wood.cumulativeDb(), 1e-9);
		assertEquals(2, stone.x());
		assertEquals("stone", stone.name());
		assertEquals(15, stone.lossDb(), 1e-9);
		assertEquals(23, stone.cumulativeDb(), 1e-9);
		assertEquals(7, view.sessionId());
		assertEquals(SOURCE, view.source());
		assertEquals(LISTENER, view.listener());
	}

	@Test
	void copiesOcclusion() {
		TestGrid grid = new TestGrid();
		for (int i = 0; i < 70; i++) {
			grid.solid(2 + 2 * i, 0, 0, material("stone"));
		}
		PathCapture capture = new PathCapture();
		new OcclusionTracer(grid, S).occlusion(0.5, 0.5, 0.5, 145.5, 0.5, 0.5, 1, 1000, new TraceBudget(1_000_000),
				capture);
		Occlusion occlusion = new Occlusion(12, 4);
		PathView view = PathView.of(1, SOURCE, LISTENER, capture, occlusion);
		assertEquals(12, view.lossDb());
		assertEquals(4, view.highLossDb());
		assertEquals(occlusion.mute(), view.mute());
		assertEquals(6, view.truncated());
		assertEquals(Occlusion.MUTE.mute(), PathView.of(1, SOURCE, LISTENER, capture, Occlusion.MUTE).mute());
	}

	@Test
	void detourPointsInOrder() {
		TestGrid room = new TestGrid().shell(0, 0, 0, 6, 4, 6, material("stone")).clear(7, 0, 1, 7, 1, 1);
		PathCapture capture = new PathCapture();
		Occlusion occlusion = Objects.requireNonNull(new OcclusionTracer(room, S).occlusion(12.5, 1.5, 3.5, 3.5, 1.5,
				3.5, 1, 64, new TraceBudget(100_000), capture));
		PathView view = PathView.of(1, new Vec(3.5, 1.5, 3.5), new Vec(12.5, 1.5, 3.5), capture, occlusion);
		assertEquals(capture.detours().size(), view.detours().size());
		for (int d = 0; d < view.detours().size(); d++) {
			PathCapture.CapturedDetour captured = capture.detours().get(d);
			Detour detour = view.detours().get(d);
			assertEquals(captured.lossDb(), detour.lossDb());
			assertEquals(captured.points().size(), detour.points().size());
			for (int i = 0; i < detour.points().size(); i++) {
				double[] p = captured.points().get(i);
				assertEquals(new Vec(p[0], p[1], p[2]), detour.points().get(i));
			}
		}
	}

	@Test
	void immutable() {
		PathCapture capture = new PathCapture();
		PathView view = PathView.of(1, SOURCE, LISTENER, capture, trace(stoneThenWood(), capture));
		assertThrows(UnsupportedOperationException.class,
				() -> view.crossings().add(new Crossing(0, 0, 0, "x", 0, 0)));
		assertThrows(UnsupportedOperationException.class, () -> view.detours().add(new Detour(List.of(), 0)));
	}
}
