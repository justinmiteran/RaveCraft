package com.ravecraft.mod.registry;

import com.ravecraft.mod.RaveCraft;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** Blocks and their block items. */
public final class ModBlocks {
	public static final Block EXAMPLE_BLOCK = register("example_block", Block::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.METAL)
					.sound(SoundType.METAL)
					.strength(3.0F, 6.0F)
					.requiresCorrectToolForDrops());

	private ModBlocks() {
	}

	/** Registers a block and its {@link BlockItem}, both under the same id. */
	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory,
			BlockBehaviour.Properties properties) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, RaveCraft.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, factory.apply(properties.setId(blockKey)));

		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, RaveCraft.id(name));
		Item.Properties itemProperties = new Item.Properties().setId(itemKey).useBlockDescriptionPrefix();
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, itemProperties));
		return block;
	}

	/** Forces class loading, hence registration. */
	public static void init() {
	}
}
