package com.ravecraft.mod.core.machine;

/** Where everything is on the machine screens: the slots (inside their border), the arrow and the energy bolt. */
public final class MachineLayout {
	/** X of the middle input slot; the used inputs are centred on it. */
	public static final int INPUT_CENTER_X = 48;
	public static final int INPUT_Y = 17;
	public static final int SLOT_SPACING = 18;
	public static final int ENERGY_X = 48;
	public static final int ENERGY_Y = 53;
	public static final int OUTPUT_X = 125;
	public static final int OUTPUT_Y = 35;
	/** The mold slot, left of the input; shown on machines that use a mold only. */
	public static final int MOLD_X = 26;
	public static final int MOLD_Y = 17;
	public static final int ARROW_X = 89;
	public static final int ARROW_Y = 34;
	public static final int ARROW_WIDTH = 24;
	public static final int ARROW_HEIGHT = 16;
	public static final int BOLT_X = 49;
	public static final int BOLT_Y = 36;
	public static final int BOLT_SIZE = 14;

	private MachineLayout() {
	}

	/**
	 * The x of an input slot, the used inputs centred above the energy slot.
	 *
	 * @param index the input index
	 * @param inputs how many inputs the machine uses
	 * @return the slot x in the screen
	 */
	public static int inputX(int index, int inputs) {
		return INPUT_CENTER_X + (2 * index - (inputs - 1)) * SLOT_SPACING / 2;
	}
}
