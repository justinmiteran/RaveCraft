package com.ravecraft.mod.core.ui;

/**
 * The arithmetic of dragging a list row to another place. A gap is a slot between rows: 0 is above row 0, n is below
 * row n−1. {@code contentY} is a y inside the scrolled content, in GUI pixels.
 */
public final class RowDrag {
	/** How far the mouse moves, in GUI pixels, before a press becomes a drag. */
	public static final int THRESHOLD = 3;

	private RowDrag() {
	}

	/**
	 * Whether a press at {@code pressY} has become a drag with the mouse now at {@code mouseY}.
	 *
	 * @param pressY where the press began
	 * @param mouseY the mouse's y now
	 * @return true once the press is a drag
	 */
	public static boolean started(double pressY, double mouseY) {
		return Math.abs(mouseY - pressY) > THRESHOLD;
	}

	/**
	 * The gap nearest to {@code contentY}, clamped to {@code [minGap, maxGap]}.
	 *
	 * @param contentY the mouse's y in the content
	 * @param rowH the row height
	 * @param minGap the first gap allowed
	 * @param maxGap the last gap allowed
	 * @return the gap index
	 */
	public static int gap(double contentY, int rowH, int minGap, int maxGap) {
		return Math.clamp(Math.round(contentY / rowH), minGap, maxGap);
	}

	/**
	 * The index among what is still to play for dropping row {@code fromRow} at {@code gap}, {@code firstGap} being
	 * the gap right after the current row; -1 when the drop leaves the row where it is.
	 *
	 * @param fromRow the row dragged
	 * @param gap the gap it is dropped at
	 * @param firstGap the gap right after the current row
	 * @return the new index, or -1 when nothing moves
	 */
	public static int moveIndex(int fromRow, int gap, int firstGap) {
		if (gap == fromRow || gap == fromRow + 1) {
			return -1;
		}
		return gap - firstGap - (firstGap <= fromRow && fromRow < gap ? 1 : 0);
	}
}
