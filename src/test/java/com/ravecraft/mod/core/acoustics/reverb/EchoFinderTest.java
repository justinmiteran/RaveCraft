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

class EchoFinderTest {
	private static final EchoSettings E = EchoSettings.DEFAULT;
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
	}

	@Test
	void woolWallDoesNotEcho() {
		assertEquals(Echo.NONE, EchoFinder.find(hits(6, 25, 0.7), E));
	}

	@Test
	void tooFewRaysDoNotEcho() {
		assertEquals(Echo.NONE, EchoFinder.find(hits(3, 25, 0.02), E));
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
		assertEquals(2 * 24.5 / 343.0, echo.delaySeconds(), 0.03); // oblique rays hit farther, so the median sits above 24.5
		assertTrue(echo.gain() > 0);
	}
}
