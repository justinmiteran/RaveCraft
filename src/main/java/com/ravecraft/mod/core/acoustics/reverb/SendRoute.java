package com.ravecraft.mod.core.acoustics.reverb;

/** Per-stream reverb send: fades out of its slot, switches, then fades into the new one. Audio thread only. */
public final class SendRoute {
	/** Slot index of the listener reverb; 1 is S1 and 2 is S2. */
	public static final int LISTENER = 0;

	private static final double FADE_MS = 100.0;

	private int slot = LISTENER;
	private double gain = 1.0;

	/** The slot the send goes to now. */
	public int slot() {
		return slot;
	}

	/** The send's gain now, from 0 to 1. */
	public double gain() {
		return gain;
	}

	/** Advances by {@code dtMs} toward {@code targetSlot}: fades out, switches, then fades back in. */
	public void step(int targetSlot, double dtMs) {
		if (!(dtMs > 0)) {
			return;
		}
		double delta = dtMs / FADE_MS;
		if (targetSlot == slot) {
			gain = Math.min(1.0, gain + delta);
		} else {
			gain = Math.max(0.0, gain - delta);
			if (gain <= 0.0) {
				gain = 0.0;
				slot = targetSlot;
			}
		}
	}
}
