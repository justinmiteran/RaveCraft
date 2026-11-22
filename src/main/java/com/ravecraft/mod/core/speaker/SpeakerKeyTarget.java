package com.ravecraft.mod.core.speaker;

import java.util.OptionalInt;
import java.util.function.IntPredicate;

/**
 * Picks which inventory slot the "open speaker" key opens: the back (chest slot), the main hand, the offhand, then the
 * hotbar and the main inventory. The other armor slots are never considered.
 */
public final class SpeakerKeyTarget {
	/**
	 * Vanilla chest equipment slot index ({@code Inventory.INVENTORY_SIZE + EquipmentSlot.CHEST.getIndex()}, 36 + 2),
	 * where a speaker is worn on the back.
	 */
	public static final int BACK = 38;
	/** Vanilla offhand slot index (Inventory.SLOT_OFFHAND); core cannot import Minecraft. */
	private static final int OFFHAND = 40;
	private static final int INVENTORY_END = 36; // hotbar 0..8 then main inventory 9..35, scanned in order

	private SpeakerKeyTarget() {}

	/**
	 * The slot of the speaker to open: the back, the selected hotbar slot, the offhand, then the hotbar and the main
	 * inventory in order; empty without a speaker.
	 *
	 * @param selectedHotbar the selected hotbar slot
	 * @param isSpeaker true for a slot holding a speaker
	 * @return the slot, or empty
	 */
	public static OptionalInt choose(int selectedHotbar, IntPredicate isSpeaker) {
		for (int slot : new int[] {BACK, selectedHotbar, OFFHAND}) {
			if (isSpeaker.test(slot)) {
				return OptionalInt.of(slot);
			}
		}
		for (int slot = 0; slot < INVENTORY_END; slot++) {
			if (isSpeaker.test(slot)) {
				return OptionalInt.of(slot);
			}
		}
		return OptionalInt.empty();
	}
}
