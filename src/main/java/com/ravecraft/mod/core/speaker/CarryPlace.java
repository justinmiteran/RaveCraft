package com.ravecraft.mod.core.speaker;

/** Where a speaker is carried: in the main or off hand, in the inventory, or on the back (chest slot). */
public enum CarryPlace {
	/** Held in a hand. */
	HAND,
	/** Stored in the inventory, not in a hand or on the back. */
	INVENTORY,
	/** Worn on the back, in the chest slot. */
	BACK
}
