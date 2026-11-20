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

	static void draw(GuiGraphicsExtractor graphics, int x, int trackTop, int track, int content, int scroll,
			boolean hot) {
		int thumbH = Scrollbar.thumbHeight(track, content);
		int thumbY = Scrollbar.thumbTop(trackTop, track, content, scroll);
		graphics.fill(x, trackTop, x + Scrollbar.WIDTH, trackTop + track, TRACK);
		graphics.fill(x, thumbY, x + Scrollbar.WIDTH, thumbY + thumbH, hot ? THUMB_HOT : THUMB);
	}
}
