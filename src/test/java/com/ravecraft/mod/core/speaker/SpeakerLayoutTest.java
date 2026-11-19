package com.ravecraft.mod.core.speaker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ravecraft.mod.core.speaker.SpeakerLayout.Rect;
import org.junit.jupiter.api.Test;

class SpeakerLayoutTest {
	@Test
	void aSmallScreenKeepsTheCompactWindow() {
		SpeakerLayout layout = SpeakerLayout.of(320, 240);
		assertFalse(layout.wide());
		assertEquals(256, layout.width());
		assertEquals(216, layout.height());
		assertEquals(242, layout.bandWidth());
		assertEquals(256, layout.controlsWidth());
		assertEquals(new Rect(7, 98, 116, 96), layout.library());
		assertEquals(new Rect(133, 98, 116, 96), layout.queue());
		assertEquals(88, layout.queueLabelY());
		assertEquals(198, layout.buttonsY());
	}

	@Test
	void aLargeScreenGetsTheWideWindowCappedAt400By300() {
		SpeakerLayout layout = SpeakerLayout.of(1270, 670);
		assertTrue(layout.wide());
		assertEquals(400, layout.width());
		assertEquals(300, layout.height());
		// Player and library on the left, the queue on the whole right side.
		assertEquals(251, layout.bandWidth());
		assertEquals(265, layout.controlsWidth());
		assertEquals(new Rect(7, 98, 251, 180), layout.library());
		assertEquals(new Rect(264, 17, 129, 261), layout.queue());
		assertEquals(7, layout.queueLabelY());
		assertEquals(282, layout.buttonsY());
	}

	@Test
	void aMidSizedScreenGetsAWindowThatFitsIt() {
		SpeakerLayout layout = SpeakerLayout.of(420, 260);
		assertTrue(layout.wide());
		assertEquals(368, layout.width());
		assertEquals(244, layout.height());
		assertEquals(new Rect(241, 17, 120, 205), layout.queue());
	}

	@Test
	void theWideLeftColumnAlwaysHasRoomForTheControls() {
		for (int screen = 300; screen <= 600; screen++) {
			SpeakerLayout layout = SpeakerLayout.of(screen, 400);
			if (layout.wide()) {
				assertTrue(layout.bandWidth() >= 220, "column at screen width " + screen);
				Rect queue = layout.queue();
				assertEquals(layout.width() - 7, queue.x() + queue.w(), "queue ends at the window's edge");
			}
		}
	}

	@Test
	void aScreenShorterThanTheWindowKeepsTheMinimumHeight() {
		assertEquals(216, SpeakerLayout.of(1000, 200).height());
	}
}
