package com.ravecraft.mod.core.acoustics.debug.view;

import com.ravecraft.mod.core.acoustics.Vec;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class WaypointTest {
	private static final Vec SOURCE = new Vec(6.5, 1.5, 0.5);
	private static final Vec LISTENER = new Vec(0.5, 1.6, 0.5);
	private static final Vec DOOR = new Vec(3.5, 1.5, -1.5);

	@Test
	void sharedWaypointIsOneWithEveryLoss() {
		Vec a = new Vec(1.5, 1.5, -2.5);
		Vec b = new Vec(1.5, 2.5, -2.5);
		List<Waypoint> waypoints = Waypoint.of(List.of(new Detour(List.of(SOURCE, DOOR, a, LISTENER), 9.9),
				new Detour(List.of(SOURCE, DOOR, b, LISTENER), 10.4)));
		assertEquals(List.of(new Waypoint(DOOR, List.of(9.9, 10.4)), new Waypoint(a, List.of(9.9)),
				new Waypoint(b, List.of(10.4))), waypoints);
	}

	@Test
	void endsAreNotWaypoints() {
		assertEquals(List.of(), Waypoint.of(List.of(new Detour(List.of(SOURCE, LISTENER), 3))));
	}
}
