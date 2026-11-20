package com.ravecraft.mod.core.acoustics.debug.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

	@Test
	void pointAwayFromTheCameraStays() {
		// Third person: the camera is 4 blocks behind the head.
		Vec camera = new Vec(1, 64.62, 0);
		Vec head = new Vec(5, 64.62, 0);
		assertEquals(head, EyeAnchor.of(head, camera));
	}
}
