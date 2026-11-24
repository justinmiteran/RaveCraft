package com.ravecraft.mod.core.machine;

/**
 * The recipe viewer page of a machine: the {@link MachineLayout} of its screen, moved to the top left corner. Slot
 * positions are those of the item, inside the slot border.
 *
 * @param inputs how many inputs the machine uses
 * @param mold whether the machine needs a mold
 */
public record MachineRecipeLayout(int inputs, boolean mold) {
	public static final int HEIGHT = 54;
	public static final int TIME_Y = 45;
	public static final int TIME_HEIGHT = 9;
	/** From the output item to the right edge of the page, past the large output border. */
	private static final int OUTPUT_MARGIN = 21;
	private static final int SHIFT_Y = MachineLayout.INPUT_Y - 1;
	private static final float TICKS_PER_SECOND = 20F;

	/**
	 * Returns the page width.
	 *
	 * @return the width in pixels
	 */
	public int width() {
		return MachineLayout.OUTPUT_X - shiftX() + OUTPUT_MARGIN;
	}

	/**
	 * Returns where an input slot is.
	 *
	 * @param index the input index
	 * @return the position of its item
	 */
	public Pos input(int index) {
		return moved(MachineLayout.inputX(index, inputs), MachineLayout.INPUT_Y);
	}

	/**
	 * Returns where the mold slot is.
	 *
	 * @return the position of its item
	 */
	public Pos moldSlot() {
		return moved(MachineLayout.MOLD_X, MachineLayout.MOLD_Y);
	}

	/**
	 * Returns where the energy slot is.
	 *
	 * @return the position of its item
	 */
	public Pos energy() {
		return moved(MachineLayout.ENERGY_X, MachineLayout.ENERGY_Y);
	}

	/**
	 * Returns where the output slot is.
	 *
	 * @return the position of its item
	 */
	public Pos output() {
		return moved(MachineLayout.OUTPUT_X, MachineLayout.OUTPUT_Y);
	}

	/**
	 * Returns where the energy bolt is.
	 *
	 * @return its top left corner
	 */
	public Pos bolt() {
		return moved(MachineLayout.BOLT_X, MachineLayout.BOLT_Y);
	}

	/**
	 * Returns where the progress arrow is.
	 *
	 * @return its top left corner
	 */
	public Pos arrow() {
		return moved(MachineLayout.ARROW_X, MachineLayout.ARROW_Y);
	}

	/**
	 * Writes an operation time in seconds, without a trailing zero.
	 *
	 * @param ticks the time in ticks
	 * @return the number of seconds
	 */
	public static String seconds(int ticks) {
		float value = ticks / TICKS_PER_SECOND;
		return value == Math.floor(value) ? Integer.toString((int) value) : Float.toString(value);
	}

	/** How far left the screen moves: its leftmost slot ends one pixel from the edge. */
	private int shiftX() {
		return (mold ? MachineLayout.MOLD_X : MachineLayout.inputX(0, inputs)) - 1;
	}

	private Pos moved(int x, int y) {
		return new Pos(x - shiftX(), y - SHIFT_Y);
	}

	/**
	 * A position in the page.
	 *
	 * @param x pixels from the left
	 * @param y pixels from the top
	 */
	public record Pos(int x, int y) {
	}
}
