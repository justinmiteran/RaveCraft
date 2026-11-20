package com.ravecraft.mod.core.speaker;

/**
 * Where the speaker screen's parts go, for a given screen size (in GUI pixels). Positions are relative to the
 * window's top-left corner.
 *
 * <p>On a small screen the window stays compact (256×216): band and controls across the top, library and queue
 * side by side underneath. With room for it the window grows with the screen up to 640×420 and becomes wide: the
 * band, the controls and the library share the left column, and the queue runs down the whole right side.
 */
public record SpeakerLayout(boolean wide, int width, int height, int bandWidth, int controlsWidth, Rect library,
		Rect queue, int queueLabelY, int titleWidth, int buttonsY, int buttonsHeight) {

	/** A rectangle relative to the window. */
	public record Rect(int x, int y, int w, int h) {
	}

	public static final int MIN_WIDTH = 256;
	public static final int MIN_HEIGHT = 216;
	public static final int MAX_WIDTH = 640;
	public static final int MAX_HEIGHT = 420;
	/** The narrowest wide window: its left column still holds the controls row. */
	static final int WIDE_MIN_WIDTH = 360;
	/** Room kept on each side: the source tabs stick out of the window's left edge. */
	private static final int SIDE_MARGIN = 26;
	private static final int TOP_MARGIN = 8;
	private static final int EDGE = 7;
	private static final int GAP = 6;
	private static final int LIBRARY_Y = 98;
	private static final int BAND_Y = 17;
	/** The compact window's bottom buttons are short; a wide one has room for vanilla's full height. */
	private static final int COMPACT_BUTTONS_H = 14;
	private static final int WIDE_BUTTONS_H = 20;
	private static final int LIST_GAP = 4;
	private static final int QUEUE_MIN_WIDTH = 120;
	private static final double QUEUE_SHARE = 0.34;

	public static SpeakerLayout of(int screenWidth, int screenHeight) {
		int width = Math.min(MAX_WIDTH, screenWidth - 2 * SIDE_MARGIN);
		if (width < WIDE_MIN_WIDTH) {
			return compact();
		}
		int height = Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, screenHeight - 2 * TOP_MARGIN));
		int buttonsY = height - EDGE - WIDE_BUTTONS_H;
		int listsBottom = buttonsY - LIST_GAP;
		int inner = width - 2 * EDGE - GAP;
		int queueWidth = Math.max(QUEUE_MIN_WIDTH, (int) Math.round(inner * QUEUE_SHARE));
		int column = inner - queueWidth;
		return new SpeakerLayout(true, width, height, column, column + 2 * EDGE,
				new Rect(EDGE, LIBRARY_Y, column, listsBottom - LIBRARY_Y),
				new Rect(EDGE + column + GAP, BAND_Y, queueWidth, listsBottom - BAND_Y),
				BAND_Y - 10, column - 1, buttonsY, WIDE_BUTTONS_H);
	}

	private static SpeakerLayout compact() {
		int buttonsY = MIN_HEIGHT - 4 - COMPACT_BUTTONS_H;
		int listHeight = buttonsY - LIST_GAP - LIBRARY_Y;
		int panel = 116;
		return new SpeakerLayout(false, MIN_WIDTH, MIN_HEIGHT, MIN_WIDTH - 2 * EDGE, MIN_WIDTH,
				new Rect(EDGE, LIBRARY_Y, panel, listHeight),
				new Rect(MIN_WIDTH - EDGE - panel, LIBRARY_Y, panel, listHeight),
				LIBRARY_Y - 10, MIN_WIDTH - 16, buttonsY, COMPACT_BUTTONS_H);
	}
}
