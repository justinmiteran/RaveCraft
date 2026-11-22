package com.ravecraft.mod.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** Convention tags shared with other mods (namespace {@code c}). */
public final class ModTags {
	private static final String COMMON = "c";

	private ModTags() {
	}

	/** Block tags. */
	public static final class Blocks {
		public static final TagKey<Block> ORES = block("ores");
		public static final TagKey<Block> ORES_NEODYMIUM = block("ores/neodymium");
		public static final TagKey<Block> ORES_OIL_SHALE = block("ores/oil_shale");
		public static final TagKey<Block> ORES_IN_GROUND_STONE = block("ores_in_ground/stone");
		public static final TagKey<Block> ORES_IN_GROUND_DEEPSLATE = block("ores_in_ground/deepslate");

		private Blocks() {
		}
	}

	/** Item tags. */
	public static final class Items {
		public static final TagKey<Item> ORES = item("ores");
		public static final TagKey<Item> ORES_NEODYMIUM = item("ores/neodymium");
		public static final TagKey<Item> ORES_OIL_SHALE = item("ores/oil_shale");
		public static final TagKey<Item> ORES_IN_GROUND_STONE = item("ores_in_ground/stone");
		public static final TagKey<Item> ORES_IN_GROUND_DEEPSLATE = item("ores_in_ground/deepslate");
		public static final TagKey<Item> RAW_MATERIALS_NEODYMIUM = item("raw_materials/neodymium");
		public static final TagKey<Item> INGOTS_NEODYMIUM = item("ingots/neodymium");
		public static final TagKey<Item> SILICON = item("silicon");

		private Items() {
		}
	}

	private static TagKey<Block> block(String path) {
		return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(COMMON, path));
	}

	private static TagKey<Item> item(String path) {
		return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(COMMON, path));
	}
}
