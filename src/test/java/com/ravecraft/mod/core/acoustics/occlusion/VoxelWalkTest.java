package com.ravecraft.mod.core.acoustics.occlusion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VoxelWalkTest {
	private static final double EPS = 1e-12;

	@Test
	void walksAStraightLineThroughEachVoxel() {
		VoxelWalk walk = new VoxelWalk();
		walk.start(0.5, 0.5, 0.5, 3.5, 0.5, 0.5);
		assertEquals(3, walk.length(), EPS);
		double[] enter = {0, 1.0 / 6, 0.5, 5.0 / 6};
		double[] exit = {1.0 / 6, 0.5, 5.0 / 6, 1};
		for (int i = 0; i < 4; i++) {
			assertEquals(i, walk.x());
			assertEquals(0, walk.y());
			assertEquals(0, walk.z());
			assertEquals(i == 0, walk.first());
			assertEquals(i == 3, walk.last());
			assertEquals(enter[i], walk.enterT(), EPS);
			assertEquals(exit[i], walk.exitT(), EPS);
			assertEquals(VoxelSample.X, walk.entryAxis());
			if (i < 3) {
				walk.step();
			}
		}
	}

	@Test
	void diagonalTiesStepTheLowestAxisFirst() {
		VoxelWalk walk = new VoxelWalk();
		walk.start(0.5, 0.5, 0.5, 1.5, 1.5, 0.5);
		int[][] expected = {{0, 0, 0}, {1, 0, 0}, {1, 1, 0}};
		for (int i = 0; i < expected.length; i++) {
			assertEquals(expected[i][0], walk.x());
			assertEquals(expected[i][1], walk.y());
			assertEquals(expected[i][2], walk.z());
			assertEquals(i == expected.length - 1, walk.last());
			if (!walk.last()) {
				walk.step();
			}
		}
	}

	@Test
	void sameVoxelIsFirstAndLast() {
		VoxelWalk walk = new VoxelWalk();
		walk.start(0.2, 0.2, 0.2, 0.8, 0.8, 0.8);
		assertTrue(walk.first());
		assertTrue(walk.last());
		assertEquals(1, walk.exitT(), EPS);
		assertEquals(0, walk.enterT(), EPS);
		assertFalse(walk.length() == 0);
	}
}
