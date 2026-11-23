package com.ravecraft.mod.datagen;

import com.ravecraft.mod.registry.ModBlocks;
import com.ravecraft.mod.registry.ModItems;
import com.ravecraft.mod.registry.ModTags;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;

/**
 * Item tags: the block items mirror their block tags, plus the convention material tags. Entries are written
 * explicitly rather than copied, so the output never depends on the order the providers run in.
 */
public class RaveCraftItemTagProvider extends FabricTagsProvider.ItemTagsProvider {
	/**
	 * A provider writing to {@code output}.
	 *
	 * @param output the pack the files go to
	 * @param registries the registries, once loaded
	 */
	public RaveCraftItemTagProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
		super(output, registries);
	}

	@Override
	protected void addTags(HolderLookup.Provider registries) {
		builder(ModTags.Items.ORES_OIL_SHALE).add(key(ModBlocks.OIL_SHALE));
		builder(ModTags.Items.ORES_NEODYMIUM).add(key(ModBlocks.DEEPSLATE_NEODYMIUM_ORE));
		builder(ModTags.Items.ORES).addTag(ModTags.Items.ORES_OIL_SHALE).addTag(ModTags.Items.ORES_NEODYMIUM);
		builder(ModTags.Items.ORES_IN_GROUND_STONE).add(key(ModBlocks.OIL_SHALE));
		builder(ModTags.Items.ORES_IN_GROUND_DEEPSLATE).add(key(ModBlocks.DEEPSLATE_NEODYMIUM_ORE));
		builder(ModTags.Items.RAW_MATERIALS_NEODYMIUM).add(key(ModItems.RAW_NEODYMIUM));
		builder(ModTags.Items.INGOTS_NEODYMIUM).add(key(ModItems.NEODYMIUM_INGOT));
		builder(ModTags.Items.SILICON).add(key(ModItems.SILICON));
		builder(ModTags.Items.STORAGE_BLOCKS_RAW_NEODYMIUM).add(key(ModBlocks.RAW_NEODYMIUM_BLOCK));
		builder(ModTags.Items.STORAGE_BLOCKS_NEODYMIUM).add(key(ModBlocks.NEODYMIUM_BLOCK));
		builder(ModTags.Items.STORAGE_BLOCKS_SILICON).add(key(ModBlocks.SILICON_BLOCK));
		builder(ModTags.Items.STORAGE_BLOCKS).addTag(ModTags.Items.STORAGE_BLOCKS_RAW_NEODYMIUM)
				.addTag(ModTags.Items.STORAGE_BLOCKS_NEODYMIUM).addTag(ModTags.Items.STORAGE_BLOCKS_SILICON);
		addAll(builder(BlockItemTags.STAIRS.item()), RaveCraftBlockTagProvider.shapes(StairBlock.class));
		addAll(builder(BlockItemTags.SLABS.item()), RaveCraftBlockTagProvider.shapes(SlabBlock.class));
		addAll(builder(BlockItemTags.WALLS.item()), RaveCraftBlockTagProvider.shapes(WallBlock.class));
	}

	private static void addAll(TagAppender<Item> tag, List<? extends ItemLike> items) {
		items.forEach(item -> tag.add(key(item)));
	}

	private static ResourceKey<Item> key(ItemLike item) {
		return BuiltInRegistries.ITEM.getResourceKey(item.asItem()).orElseThrow();
	}
}
