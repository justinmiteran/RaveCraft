package com.ravecraft.mod.core.acoustics.reverb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RoomShapeTest {
	private static final double MIN_RT60 = 0.1;

	/** Eyring, the closed-room formula the RT60 uses when no ray escapes. */
	private static double eyring(double meanFreePath, double absorption) {
		return 0.161 * meanFreePath / (-4 * Math.log(1 - absorption));
	}

	@Test
	void openFieldHasNoTail() {
		// Flat ground under the listener: half the rays escape, the rest land on the ground ~7.7 blocks away.
		assertEquals(MIN_RT60, new RoomShape(0.5, 7.67, 0.75, 0.5, 0).rt60(6), 1e-9);
		// Even on stone ground, which Eyring alone gives 0.43 s.
		assertEquals(MIN_RT60, new RoomShape(0.5, 7.67, 0.51, 0.02, 0).rt60(6), 1e-9);
	}

	@Test
	void closedRoomKeepsEyring() {
		assertEquals(eyring(5, 0.1), new RoomShape(0, 5, 0.1, 0.1, 0.5).rt60(6), 1e-9);
		assertEquals(eyring(3, 0.7), new RoomShape(0, 3, 0.7, 0.7, 0.5).rt60(6), 1e-9);
	}

	@Test
	void openDoorShortensTheTail() {
		double closed = new RoomShape(0, 5, 0.1, 0.1, 0.5).rt60(6);
		// The same room with 5 % of the rays escaping through a door: the escapes count as absorption 1.
		double door = new RoomShape(0.05, 5, 0.05 + 0.95 * 0.1, 0.1, 0.5).rt60(6);
		assertTrue(door < closed, "door " + door + " closed " + closed);
		assertEquals(eyring(5, 0.145) * Math.pow(0.95, 3), door, 1e-9);
	}

	@Test
	void boundsStillHold() {
		assertEquals(6, new RoomShape(0, 5, 0, 0, 0.5).rt60(6));
		assertEquals(MIN_RT60, new RoomShape(1, 64, 1, 1, 0.5).rt60(6), 1e-9);
		assertEquals(MIN_RT60, new RoomShape(0.2, 1, 0.9, 0.9, 0.5).rt60(0.05), 1e-9);
	}
}
