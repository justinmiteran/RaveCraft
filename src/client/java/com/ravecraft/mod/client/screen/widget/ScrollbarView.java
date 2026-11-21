package com.ravecraft.mod.client.screen.widget;

import com.ravecraft.mod.core.ui.Scrollbar;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Draws a list's {@link Scrollbar}: a dark track and a light thumb, white while hovered or dragged. */
final class ScrollbarView {
	private static final int TRACK = 0xFF555555;
	private static final int THUMB = 0xFFE0E0E0;
	private static final int THUMB_HOT = 0xFFFFFFFF;

	private ScrollbarView() {
	}

	/** Where the scrollbar runs: from {@code top}, {@code height} high, at {@code x}. */
	record Track(int x, int top, int height) {
	}

	/** Draws the bar on {@code track} for {@code content} pixels scrolled by {@code scroll}; {@code hot} lights it. */
	static void draw(GuiGraphicsExtractor graphics, Track track, int content, int scroll, boolean hot) {
		int x = track.x();
		int thumbH = Scrollbar.thumbHeight(track.height(), content);
		int thumbY = Scrollbar.thumbTop(track.top(), track.height(), content, scroll);
		graphics.fill(x, track.top(), x + Scrollbar.WIDTH, track.top() + track.height(), TRACK);
		graphics.fill(x, thumbY, x + Scrollbar.WIDTH, thumbY + thumbH, hot ? THUMB_HOT : THUMB);
	}
}
