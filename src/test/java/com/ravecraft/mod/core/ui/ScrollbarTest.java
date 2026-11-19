package com.ravecraft.mod.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScrollbarTest {
	// A 100 px track at y = 10 over 400 px of rows: a 25 px thumb, 300 px of scroll.
	private static final int TOP = 10;
	private static final int TRACK = 100;
	private static final int CONTENT = 400;

	@Test
	void theThumbIsAsTallAsTheVisibleShareButNeverTiny() {
		assertEquals(25, Scrollbar.thumbHeight(TRACK, CONTENT));
		assertEquals(Scrollbar.MIN_THUMB, Scrollbar.thumbHeight(TRACK, 100_000));
	}

	@Test
	void theThumbFollowsTheScroll() {
		assertEquals(TOP, Scrollbar.thumbTop(TOP, TRACK, CONTENT, 0));
		assertEquals(TOP + 75, Scrollbar.thumbTop(TOP, TRACK, CONTENT, 300));
	}

	@Test
	void grabbingTheThumbDoesNotMoveItThenDraggingCarriesIt() {
		Scrollbar bar = new Scrollbar();
		// Thumb at 10..35 for scroll 0; grabbed 5 px below its top.
		assertEquals(0, bar.press(15, TOP, TRACK, CONTENT, 0));
		assertTrue(bar.dragging());
		assertEquals(120, bar.drag(45, TOP, TRACK, CONTENT), "thumb top 40 = 30 px of 75 = 120 px of 300");
		assertEquals(300, bar.drag(500, TOP, TRACK, CONTENT), "clamped at the end");
		assertEquals(0, bar.drag(-50, TOP, TRACK, CONTENT), "clamped at the start");
		bar.release();
		assertFalse(bar.dragging());
	}

	@Test
	void pressingTheTrackJumpsTheThumbUnderTheMouse() {
		Scrollbar bar = new Scrollbar();
		// The thumb's middle goes to y = 72.5 → top 60, 50 px of 75.
		assertEquals(200, bar.press(72, TOP, TRACK, CONTENT, 0));
		assertTrue(bar.dragging());
	}

	@Test
	void theGrabAreaIsTheBarAndTheMarginsBesideIt() {
		assertTrue(Scrollbar.hits(98, 100, 104));
		assertTrue(Scrollbar.hits(103, 100, 104));
		assertFalse(Scrollbar.hits(97, 100, 104));
		assertFalse(Scrollbar.hits(104, 100, 104));
	}
}
