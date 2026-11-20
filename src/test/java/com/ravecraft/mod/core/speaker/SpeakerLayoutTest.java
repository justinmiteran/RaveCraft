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
		assertEquals(14, layout.buttonsHeight());
	}

	@Test
	void aLargeScreenGetsTheWideWindowCappedAt640By420() {
		SpeakerLayout layout = SpeakerLayout.of(1270, 670);
		assertTrue(layout.wide());
		assertEquals(640, layout.width());
		assertEquals(420, layout.height());
		// Player and library on the left, the queue on the whole right side.
		assertEquals(409, layout.bandWidth());
		assertEquals(423, layout.controlsWidth());
		assertEquals(new Rect(7, 98, 409, 291), layout.library());
		assertEquals(new Rect(422, 17, 211, 372), layout.queue());
		assertEquals(7, layout.queueLabelY());
		// Full-height vanilla buttons at the bottom of a wide window.
		assertEquals(393, layout.buttonsY());
		assertEquals(20, layout.buttonsHeight());
	}

	@Test
	void aSmallGameWindowIsFilledAllButItsMargins() {
		// 1146x750 pixels at GUI scale 2: the window used to stop at 400x300.
		SpeakerLayout layout = SpeakerLayout.of(573, 375);
		assertEquals(521, layout.width());
		assertEquals(359, layout.height());
	}

	@Test
	void aMidSizedScreenGetsAWindowThatFitsIt() {
		SpeakerLayout layout = SpeakerLayout.of(420, 260);
		assertTrue(layout.wide());
		assertEquals(368, layout.width());
		assertEquals(244, layout.height());
		assertEquals(new Rect(241, 17, 120, 196), layout.queue());
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
