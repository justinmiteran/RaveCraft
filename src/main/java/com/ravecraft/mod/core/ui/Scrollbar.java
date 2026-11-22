package com.ravecraft.mod.core.ui;

/**
 * The thin vertical scrollbar of the screens' lists: where its thumb goes, and the scroll that dragging it gives.
 * Grabbing the thumb keeps the point grabbed under the mouse; pressing the track elsewhere first jumps the thumb's
 * middle under the mouse. All values are GUI pixels; {@code content} is the height of every row together.
 */
public final class Scrollbar {
	public static final int WIDTH = 2;
	public static final int MIN_THUMB = 8;
	/** How far left of the bar a press still grabs it: the bar alone is too thin to aim at. */
	private static final int REACH = 2;

	/** Distance from the thumb's top to the point grabbed, or -1 when not dragging. */
	private int grab = -1;

	/**
	 * The thumb's height for {@code content} pixels shown in a {@code track} pixels high; never below the minimum.
	 *
	 * @param track the track's height
	 * @param content the content's height
	 * @return the thumb's height
	 */
	public static int thumbHeight(int track, int content) {
		return Math.max(MIN_THUMB, track * track / content);
	}

	/**
	 * The thumb's top for a scroll of {@code scroll} pixels.
	 *
	 * @param trackTop the track's top
	 * @param track the track's height
	 * @param content the content's height
	 * @param scroll the scroll, in pixels
	 * @return the thumb's top
	 */
	public static int thumbTop(int trackTop, int track, int content, int scroll) {
		int max = content - track;
		return max <= 0 ? trackTop : trackTop + (track - thumbHeight(track, content)) * scroll / max;
	}

	/**
	 * True when {@code mouseX} is on the bar at {@code barX} or in the margins around it, up to {@code right}.
	 *
	 * @param mouseX the mouse's x
	 * @param barX the bar's left edge
	 * @param right the right edge of the margin
	 * @return true when the mouse is on the bar
	 */
	public static boolean hits(double mouseX, int barX, int right) {
		return mouseX >= barX - REACH && mouseX < right;
	}

	/**
	 * Starts dragging from a press at {@code mouseY}; returns the new scroll.
	 *
	 * @param mouseY the mouse's y
	 * @param trackTop the track's top
	 * @param track the track's height
	 * @param content the content's height
	 * @param scroll the scroll now
	 * @return the new scroll
	 */
	public int press(double mouseY, int trackTop, int track, int content, int scroll) {
		int thumb = thumbHeight(track, content);
		int top = thumbTop(trackTop, track, content, scroll);
		grab = mouseY >= top && mouseY < top + thumb ? (int) mouseY - top : thumb / 2;
		return drag(mouseY, trackTop, track, content);
	}

	/**
	 * The scroll for the mouse now at {@code mouseY}, while dragging.
	 *
	 * @param mouseY the mouse's y
	 * @param trackTop the track's top
	 * @param track the track's height
	 * @param content the content's height
	 * @return the new scroll
	 */
	public int drag(double mouseY, int trackTop, int track, int content) {
		int max = content - track;
		int room = track - thumbHeight(track, content);
		if (max <= 0 || room <= 0) {
			return 0;
		}
		int top = (int) mouseY - grab - trackTop;
		return Math.clamp((long) top * max / room, 0, max);
	}

	/**
	 * Whether the thumb is being dragged.
	 *
	 * @return true while dragging
	 */
	public boolean dragging() {
		return grab >= 0;
	}

	/** Ends a drag. */
	public void release() {
		grab = -1;
	}
}
