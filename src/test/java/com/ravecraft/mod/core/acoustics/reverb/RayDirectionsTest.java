package com.ravecraft.mod.core.acoustics.reverb;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RayDirectionsTest {
	@Test
	void unitAndSpread() {
		for (int n : new int[] {16, 32, 48}) {
			double[][] dirs = RayDirections.sphere(n);
			assertEquals(n, dirs.length);
			double minAngle = 180;
			for (int i = 0; i < n; i++) {
				double[] a = dirs[i];
				assertEquals(1, Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]), 1e-9);
				for (int j = i + 1; j < n; j++) {
					double[] b = dirs[j];
					double dot = Math.clamp(a[0] * b[0] + a[1] * b[1] + a[2] * b[2], -1, 1);
					minAngle = Math.min(minAngle, Math.toDegrees(Math.acos(dot)));
				}
			}
			assertTrue(minAngle > 10, "n=" + n + " min angle " + minAngle);
			assertArrayEquals(dirs, RayDirections.sphere(n));
		}
	}
}
