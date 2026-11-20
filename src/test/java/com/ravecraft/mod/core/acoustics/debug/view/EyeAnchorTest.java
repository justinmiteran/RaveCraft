package com.ravecraft.mod.core.acoustics.debug.view;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class EyeAnchorTest {
	@Test
	void pointAtTheEyeDropsBelowIt() {
		Vec eye = new Vec(5, 64.62, 0);
		assertEquals(new Vec(5, 64.62 - EyeAnchor.DROP, 0), EyeAnchor.of(eye, eye));
	}

	@Test
	void pointNearTheEyeDropsToo() {
		Vec camera = new Vec(5, 64.62, 0);
		Vec point = new Vec(5.8, 64.62, 0.5);
		assertEquals(new Vec(5.8, 64.62 - EyeAnchor.DROP, 0.5), EyeAnchor.of(point, camera));
	}

	private static final Vec CAMERA = new Vec(0, 0, 0);
	private static final Vec FORWARD = new Vec(1, 0, 0);

	@Test
	void segmentInFrontIsKept() {
		Vec[] clipped = EyeAnchor.clip(new Vec(2, 0, 0), new Vec(6, 1, 0), CAMERA, FORWARD, 0.2);
		assertArrayEquals(new Vec[] {new Vec(2, 0, 0), new Vec(6, 1, 0)}, clipped);
	}

	@Test
	void segmentBehindIsDropped() {
		assertNull(EyeAnchor.clip(new Vec(-2, 0, 0), new Vec(-6, 1, 0), CAMERA, FORWARD, 0.2));
	}

	@Test
	void segmentToTheEyeStopsInFrontOfIt() {
		// From a speaker 6 blocks ahead to 0.6 below the eye: cut where it is 0.2 block ahead.
		Vec[] clipped = EyeAnchor.clip(new Vec(6, 0, 0), new Vec(0, -0.6, 0), CAMERA, FORWARD, 0.2);
		assertNotNull(clipped);
		assertEquals(new Vec(6, 0, 0), clipped[0]);
		assertEquals(0.2, clipped[1].x(), 1e-9);
		assertEquals(-0.6 * (6 - 0.2) / 6, clipped[1].y(), 1e-9);
	}

	@Test
	void segmentFromBehindStartsInFront() {
		Vec[] clipped = EyeAnchor.clip(new Vec(-1, 0, 0), new Vec(3, 0, 0), CAMERA, FORWARD, 0.2);
		assertNotNull(clipped);
		assertEquals(0.2, clipped[0].x(), 1e-9);
		assertEquals(new Vec(3, 0, 0), clipped[1]);
	}

	@Test
	void pointAwayFromTheCameraStays() {
		// Third person: the camera is 4 blocks behind the head.
		Vec camera = new Vec(1, 64.62, 0);
		Vec head = new Vec(5, 64.62, 0);
		assertEquals(head, EyeAnchor.of(head, camera));
	}
}
