package com.ravecraft.mod.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RowDragTest {
	// Rows: 0 current, 1..3 upcoming (first gap 1), 5 the separator, 6.. played.
	private static final int FIRST_GAP = 1;

	@Test
	void startsPastThreshold() {
		assertFalse(RowDrag.started(10, 13));
		assertTrue(RowDrag.started(10, 13.5));
		assertTrue(RowDrag.started(10, 6.5));
	}

	@Test
	void gapRoundsAndClamps() {
		assertEquals(1, RowDrag.gap(0, 12, 1, 5));
		assertEquals(1, RowDrag.gap(17, 12, 0, 5));
		assertEquals(2, RowDrag.gap(19, 12, 0, 5));
		assertEquals(5, RowDrag.gap(500, 12, 1, 5));
	}

	@Test
	void moveIndexUp() {
		assertEquals(0, RowDrag.moveIndex(3, 1, FIRST_GAP));
	}

	@Test
	void moveIndexDown() {
		assertEquals(2, RowDrag.moveIndex(1, 4, FIRST_GAP));
	}

	@Test
	void moveIndexNoOp() {
		assertEquals(-1, RowDrag.moveIndex(2, 2, FIRST_GAP));
		assertEquals(-1, RowDrag.moveIndex(2, 3, FIRST_GAP));
	}

	@Test
	void moveIndexFromPlayed() {
		assertEquals(1, RowDrag.moveIndex(6, 2, FIRST_GAP));
	}
}
