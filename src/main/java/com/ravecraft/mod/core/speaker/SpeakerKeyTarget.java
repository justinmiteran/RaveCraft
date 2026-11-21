package com.ravecraft.mod.core.speaker;

import java.util.OptionalInt;
import java.util.function.IntPredicate;

/**
 * Picks which inventory slot the "open speaker" key opens: main hand, then offhand, then the
 * hotbar, then the main inventory. Armor slots are never considered.
 */
public final class SpeakerKeyTarget {
	/** Vanilla offhand slot index (Inventory.SLOT_OFFHAND); core cannot import Minecraft. */
	private static final int OFFHAND = 40;
	private static final int INVENTORY_END = 36; // hotbar 0..8 then main inventory 9..35, scanned in order

	private SpeakerKeyTarget() {}

	public static OptionalInt choose(int selectedHotbar, IntPredicate isSpeaker) {
		if (isSpeaker.test(selectedHotbar)) {
			return OptionalInt.of(selectedHotbar);
		}
		if (isSpeaker.test(OFFHAND)) {
			return OptionalInt.of(OFFHAND);
		}
		for (int slot = 0; slot < INVENTORY_END; slot++) {
			if (isSpeaker.test(slot)) {
				return OptionalInt.of(slot);
			}
		}
		return OptionalInt.empty();
	}
}
