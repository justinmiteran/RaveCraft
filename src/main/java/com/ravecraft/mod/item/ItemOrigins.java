package com.ravecraft.mod.item;

import com.ravecraft.mod.registry.ModItems;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.item.Item;

/**
 * Which part each mold makes (spec 6.3). A static table with no recipe access, so the client tooltip can read it; a
 * GameTest keeps it equal to the loaded extruder recipes.
 */
public final class ItemOrigins {
	private ItemOrigins() {
	}

	/**
	 * The part a mold makes in the extruder.
	 *
	 * @param mold the item
	 * @return the product, or empty if the item is no mold
	 */
	public static Optional<Item> moldFor(Item mold) {
		return Optional.ofNullable(Tables.MOLD_FOR.get(mold));
	}

	/** The table, built on first use (after registration). */
	private static final class Tables {
		static final Map<Item, Item> MOLD_FOR = Map.of(
				ModItems.SHEET_MOLD, ModItems.PLASTIC_SHEET,
				ModItems.CASING_MOLD, ModItems.PLASTIC_CASING,
				ModItems.MEMBRANE_MOLD, ModItems.REINFORCED_MEMBRANE,
				ModItems.BOARD_MOLD, ModItems.CIRCUIT_BOARD);

		private Tables() {
		}
	}
}
