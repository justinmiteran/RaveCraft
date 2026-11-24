package com.ravecraft.mod.registry;

import com.ravecraft.mod.RaveCraft;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/** Standalone items (block items live in {@link ModBlocks}). */
public final class ModItems {
	public static final Item BITUMEN = register("bitumen", Item::new);
	public static final Item RAW_NEODYMIUM = register("raw_neodymium", Item::new);
	public static final Item NEODYMIUM_INGOT = register("neodymium_ingot", Item::new);
	public static final Item SILICON = register("silicon", Item::new);
	public static final Item SILICON_WAFER = register("silicon_wafer", Item::new);
	public static final Item COPPER_WIRE = register("copper_wire", Item::new);
	public static final Item VOICE_COIL = register("voice_coil", Item::new);
	public static final Item IRON_MAGNET = register("iron_magnet", Item::new);
	public static final Item PLASTIC_SHEET = register("plastic_sheet", Item::new);
	public static final Item PLASTIC_CASING = register("plastic_casing", Item::new);
	public static final Item CIRCUIT_BOARD = register("circuit_board", Item::new);
	public static final Item SIMPLE_CIRCUIT = register("simple_circuit", Item::new);
	public static final Item NEODYMIUM_MAGNET = register("neodymium_magnet", Item::new);
	public static final Item REINFORCED_MEMBRANE = register("reinforced_membrane", Item::new);
	public static final Item CHIP = register("chip", Item::new);
	public static final Item SHEET_MOLD = registerMold("sheet_mold");
	public static final Item CASING_MOLD = registerMold("casing_mold");
	public static final Item MEMBRANE_MOLD = registerMold("membrane_mold");
	public static final Item BOARD_MOLD = registerMold("board_mold");

	private ModItems() {
	}

	/**
	 * The raw materials, in creative tab order.
	 *
	 * @return the material items
	 */
	public static List<Item> materials() {
		return List.of(BITUMEN, RAW_NEODYMIUM, NEODYMIUM_INGOT, SILICON, SILICON_WAFER);
	}

	/**
	 * The crafted components, in creative tab order.
	 *
	 * @return the component items
	 */
	public static List<Item> components() {
		return List.of(COPPER_WIRE, VOICE_COIL, IRON_MAGNET, PLASTIC_SHEET, PLASTIC_CASING, CIRCUIT_BOARD,
				SIMPLE_CIRCUIT, NEODYMIUM_MAGNET, REINFORCED_MEMBRANE, CHIP);
	}

	/**
	 * The extruder molds, in creative tab order.
	 *
	 * @return the mold items
	 */
	public static List<Item> molds() {
		return List.of(SHEET_MOLD, CASING_MOLD, MEMBRANE_MOLD, BOARD_MOLD);
	}

	/** Registers an item, giving its properties the id first. */
	private static Item register(String name, Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, RaveCraft.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key)));
	}

	/** Registers a mold: a tool that is never consumed, one per stack. */
	private static Item registerMold(String name) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, RaveCraft.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(1)));
	}

	/** Forces class loading, hence registration. */
	public static void init() {
	}
}
