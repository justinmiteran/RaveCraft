package com.ravecraft.mod.item;

import com.ravecraft.mod.registry.ModBlocks;
import com.ravecraft.mod.registry.ModItems;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * Which machine makes each machine product, and which part each mold makes (spec 6.3). Static tables with no recipe
 * access, so the client tooltip can read them; a GameTest keeps them equal to the loaded machine recipes.
 */
public final class ItemOrigins {
	private ItemOrigins() {
	}

	/**
	 * The machine that makes an item.
	 *
	 * @param item the item
	 * @return the machine block, or empty if no machine makes it
	 */
	public static Optional<Block> madeIn(Item item) {
		return Optional.ofNullable(Tables.MADE_IN.get(item));
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

	/** The tables, built on first use (after registration). */
	private static final class Tables {
		static final Map<Item, Block> MADE_IN = Map.ofEntries(
				Map.entry(ModItems.COPPER_WIRE, ModBlocks.COIL_WINDER),
				Map.entry(ModItems.VOICE_COIL, ModBlocks.COIL_WINDER),
				Map.entry(ModItems.IRON_MAGNET, ModBlocks.MAGNETIZER),
				Map.entry(ModItems.NEODYMIUM_MAGNET, ModBlocks.MAGNETIZER),
				Map.entry(ModItems.SIMPLE_CIRCUIT, ModBlocks.ETCHER),
				Map.entry(ModItems.CHIP, ModBlocks.ETCHER),
				Map.entry(ModItems.PLASTIC_SHEET, ModBlocks.EXTRUDER),
				Map.entry(ModItems.PLASTIC_CASING, ModBlocks.EXTRUDER),
				Map.entry(ModItems.REINFORCED_MEMBRANE, ModBlocks.EXTRUDER),
				Map.entry(ModItems.CIRCUIT_BOARD, ModBlocks.EXTRUDER));
		static final Map<Item, Item> MOLD_FOR = Map.of(
				ModItems.SHEET_MOLD, ModItems.PLASTIC_SHEET,
				ModItems.CASING_MOLD, ModItems.PLASTIC_CASING,
				ModItems.MEMBRANE_MOLD, ModItems.REINFORCED_MEMBRANE,
				ModItems.BOARD_MOLD, ModItems.CIRCUIT_BOARD);

		private Tables() {
		}
	}
}
