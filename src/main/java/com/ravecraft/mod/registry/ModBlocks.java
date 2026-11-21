package com.ravecraft.mod.registry;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.block.PortableSpeakerBlock;
import com.ravecraft.mod.item.PortableSpeakerItem;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** Blocks and their block items. */
public final class ModBlocks {
	public static final Block PORTABLE_SPEAKER = register("portable_speaker", PortableSpeakerBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_GRAY)
					.sound(SoundType.METAL)
					.strength(1.0F)
					.noOcclusion(),
			PortableSpeakerItem::new, new Item.Properties().stacksTo(1));

	private ModBlocks() {
	}

	/** Registers a block and the item {@code itemFactory} makes for it, both under the same id. */
	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory,
			BlockBehaviour.Properties properties, BiFunction<Block, Item.Properties, Item> itemFactory,
			Item.Properties baseItemProperties) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, RaveCraft.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, factory.apply(properties.setId(blockKey)));

		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, RaveCraft.id(name));
		Item.Properties itemProperties = baseItemProperties.setId(itemKey).useBlockDescriptionPrefix();
		Registry.register(BuiltInRegistries.ITEM, itemKey, itemFactory.apply(block, itemProperties));
		return block;
	}

	/** Forces class loading, hence registration. */
	public static void init() {
	}
}
