package com.ravecraft.mod.core.acoustics.reverb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ravecraft.mod.core.acoustics.occlusion.AcousticMaterial;
import com.ravecraft.mod.core.acoustics.occlusion.OcclusionSettings;
import com.ravecraft.mod.core.acoustics.occlusion.TestGrid;
import com.ravecraft.mod.core.acoustics.occlusion.TraceBudget;
import com.ravecraft.mod.core.acoustics.occlusion.VoxelSample;

class EchoFinderTest {
	private static final EchoSettings E = EchoSettings.DEFAULT;
	private static final AcousticMaterial EARTH = OcclusionSettings.DEFAULT.materials().stream()
			.filter(m -> m.name().equals("earth")).findFirst().orElseThrow();
	private static final AcousticMaterial STONE = OcclusionSettings.DEFAULT.materials().stream()
			.filter(m -> m.name().equals("stone")).findFirst().orElseThrow();

	/** 32 hits: {@code count} walls spread over [d - 0.5, d + 0.5] with {@code absorption}, the rest open. */
	private static List<RayHit> hits(int count, double d, double absorption) {
		List<RayHit> hits = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			hits.add(new RayHit(false, d - 0.5 + i / (double) Math.max(1, count - 1), absorption));
		}
		while (hits.size() < 32) {
			hits.add(new RayHit(true, 64, 1));
		}
		return hits;
	}

	@Test
	void stoneWallAt25BlocksEchoes() {
		Echo echo = EchoFinder.find(hits(6, 25, 0.02), E);
		assertEquals(2 * 25 / 343.0, echo.delaySeconds(), 0.003);
		assertTrue(echo.gain() > 0);
	}

	@Test
	void wallAt50BlocksDoesNotEcho() {
		assertEquals(Echo.NONE, EchoFinder.find(hits(6, 50, 0.02), E));
		assertTrue(EchoFinder.group(hits(6, 50, 0.02), E).isEmpty());
	}

	@Test
	void woolWallDoesNotEcho() {
		assertEquals(Echo.NONE, EchoFinder.find(hits(6, 25, 0.7), E));
		assertTrue(EchoFinder.group(hits(6, 25, 0.7), E).isEmpty());
	}

	@Test
	void tooFewRaysDoNotEcho() {
		assertEquals(Echo.NONE, EchoFinder.find(hits(3, 25, 0.02), E));
		assertTrue(EchoFinder.group(hits(3, 25, 0.02), E).isEmpty());
	}

	@Test
	void groupIsTheWallRays() {
		assertEquals(List.of(0, 1, 2, 3, 4, 5), EchoFinder.group(hits(6, 25, 0.02), E));
	}

	@Test
	void groupIndicesSurviveShuffle() {
		List<Integer> walls = List.of(3, 10, 17, 24, 31, 5);
		List<RayHit> hits = new ArrayList<>();
		for (int i = 0; i < 32; i++) {
			hits.add(new RayHit(true, 64, 1));
		}
		for (int i = 0; i < walls.size(); i++) {
			hits.set(walls.get(i), new RayHit(false, 24.5 + i / 5.0, 0.02));
		}
		assertEquals(walls.stream().sorted().toList(), EchoFinder.group(hits, E));
	}

	@Test
	void largerGroupWins() {
		List<RayHit> hits = new ArrayList<>(hits(4, 20, 0.02).subList(0, 4));
		hits.addAll(hits(6, 30, 0.02).subList(0, 6));
		while (hits.size() < 32) {
			hits.add(new RayHit(true, 64, 1));
		}
		assertEquals(2 * 30 / 343.0, EchoFinder.find(hits, E).delaySeconds(), 0.003);
	}

	@Test
	void probeFindsAStoneWallAt25Blocks() {
		TestGrid grid = new TestGrid().fill(25, -20, -20, 25, 20, 20, STONE).fill(-3, -1, -3, 3, -1, 3, STONE);
		RoomProbe probe = new RoomProbe(grid);
		probe.start(0.5, 0.5, 0.5, 128, 64);
		assertTrue(probe.run(new TraceBudget(Integer.MAX_VALUE)));
		Echo echo = EchoFinder.find(probe.hits(), E);
		assertEquals(2 * 24.5 / 343.0, echo.delaySeconds(), 0.001);
		assertTrue(echo.gain() > 0);
	}

	@Test
	void obliqueRaysOnOneWallShareItsDistance() {
		// Rays meet the same wall 20 blocks away at angles that put them 20 to 32 blocks along: one echo at 20.
		List<RayHit> hits = new ArrayList<>();
		for (double along : new double[] {20, 22, 25, 28, 32}) {
			hits.add(new RayHit(false, along, 0.02, VoxelSample.X, 20, 20));
		}
		while (hits.size() < 32) {
			hits.add(new RayHit(true, 64, 1));
		}
		assertEquals(List.of(0, 1, 2, 3, 4), EchoFinder.group(hits, E));
		assertEquals(2 * 20 / 343.0, EchoFinder.find(hits, E).delaySeconds(), 1e-9);
	}

	@Test
	void flatCliffEchoesAtMediumQuality() {
		// A flat stone cliff 81 blocks wide, 20 blocks away on open ground, probed with the 32 rays of the medium quality.
		TestGrid grid = new TestGrid().fill(-60, -1, -40, 60, -1, 40, EARTH).fill(-40, 0, 20, 40, 29, 20, STONE);
		RoomProbe probe = new RoomProbe(grid);
		probe.start(0.5, 1.62, 0.5, 32, 64);
		assertTrue(probe.run(new TraceBudget(Integer.MAX_VALUE)));
		assertEquals(2 * 19.5 / 343.0, EchoFinder.find(probe.hits(), E).delaySeconds(), 1e-9);
	}

	@Test
	void stoneGroundDoesNotEcho() {
		// Grazing rays land on the ground 17 to 35 blocks away, but the ground is right under the listener.
		TestGrid grid = new TestGrid().fill(-70, -1, -70, 70, -1, 70, STONE);
		RoomProbe probe = new RoomProbe(grid);
		probe.start(0.5, 1.62, 0.5, 48, 64);
		assertTrue(probe.run(new TraceBudget(Integer.MAX_VALUE)));
		assertEquals(Echo.NONE, EchoFinder.find(probe.hits(), E));
	}
}
