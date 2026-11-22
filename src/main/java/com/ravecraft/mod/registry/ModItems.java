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

	/** Registers an item, giving its properties the id first. */
	private static Item register(String name, Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, RaveCraft.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key)));
	}

	/** Forces class loading, hence registration. */
	public static void init() {
	}
}
