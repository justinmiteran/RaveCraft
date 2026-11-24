package com.ravecraft.mod.datagen;

import com.ravecraft.mod.core.acoustics.occlusion.OcclusionSettings;
import com.ravecraft.mod.registry.ModBlocks;
import com.ravecraft.mod.registry.ModTags;
import com.ravecraft.mod.world.acoustics.AcousticTags;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;

/** Block tags: tool requirements, shapes, convention ore tags and acoustic materials. */
public class RaveCraftBlockTagProvider extends FabricTagsProvider.BlockTagsProvider {
	/**
	 * A provider writing to {@code output}.
	 *
	 * @param output the pack the files go to
	 * @param registries the registries, once loaded
	 */
	public RaveCraftBlockTagProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
		super(output, registries);
	}

	@Override
	protected void addTags(HolderLookup.Provider registries) {
		TagAppender<Block> pickaxe = builder(BlockTags.MINEABLE_WITH_PICKAXE);
		addAll(pickaxe, ModBlocks.speakers());
		addAll(pickaxe, ModBlocks.ores());
		addAll(pickaxe, ModBlocks.shaleFamily());
		addAll(pickaxe, ModBlocks.storageBlocks());
		addAll(pickaxe, ModBlocks.machines());
		addAll(builder(BlockTags.NEEDS_STONE_TOOL).add(key(ModBlocks.OIL_SHALE)).add(key(ModBlocks.SILICON_BLOCK)),
				ModBlocks.machines());
		builder(BlockTags.NEEDS_IRON_TOOL).add(key(ModBlocks.DEEPSLATE_NEODYMIUM_ORE))
				.add(key(ModBlocks.RAW_NEODYMIUM_BLOCK)).add(key(ModBlocks.NEODYMIUM_BLOCK));
		addAll(builder(BlockTags.STAIRS), shapes(StairBlock.class));
		addAll(builder(BlockTags.SLABS), shapes(SlabBlock.class));
		addAll(builder(BlockTags.WALLS), shapes(WallBlock.class));
		ores();
		storageBlocks();
		acoustics();
	}

	/** The convention storage block tags; {@code c:storage_blocks} holds its per-material sub-tags. */
	private void storageBlocks() {
		builder(ModTags.Blocks.STORAGE_BLOCKS_RAW_NEODYMIUM).add(key(ModBlocks.RAW_NEODYMIUM_BLOCK));
		builder(ModTags.Blocks.STORAGE_BLOCKS_NEODYMIUM).add(key(ModBlocks.NEODYMIUM_BLOCK));
		builder(ModTags.Blocks.STORAGE_BLOCKS_SILICON).add(key(ModBlocks.SILICON_BLOCK));
		builder(ModTags.Blocks.STORAGE_BLOCKS).addTag(ModTags.Blocks.STORAGE_BLOCKS_RAW_NEODYMIUM)
				.addTag(ModTags.Blocks.STORAGE_BLOCKS_NEODYMIUM).addTag(ModTags.Blocks.STORAGE_BLOCKS_SILICON);
	}

	/** The convention ore tags; {@code c:ores} holds its per-ore sub-tags, as other mods expect. */
	private void ores() {
		builder(ModTags.Blocks.ORES_OIL_SHALE).add(key(ModBlocks.OIL_SHALE));
		builder(ModTags.Blocks.ORES_NEODYMIUM).add(key(ModBlocks.DEEPSLATE_NEODYMIUM_ORE));
		builder(ModTags.Blocks.ORES).addTag(ModTags.Blocks.ORES_OIL_SHALE).addTag(ModTags.Blocks.ORES_NEODYMIUM);
		builder(ModTags.Blocks.ORES_IN_GROUND_STONE).add(key(ModBlocks.OIL_SHALE));
		builder(ModTags.Blocks.ORES_IN_GROUND_DEEPSLATE).add(key(ModBlocks.DEEPSLATE_NEODYMIUM_ORE));
	}

	/** Acoustic materials: looked at before the footstep sound, so datapacks can override them. */
	private void acoustics() {
		for (String name : OcclusionSettings.BASE_MATERIALS) {
			builder(AcousticTags.material(name));
		}
		builder(AcousticTags.material("foliage")).addOptionalTag(BlockTags.LEAVES);
		builder(AcousticTags.material("wool")).addOptionalTag(BlockTags.WOOL).addOptionalTag(BlockTags.WOOL_CARPETS);
		builder(AcousticTags.material("wood")).addOptionalTag(BlockTags.PLANKS).addOptionalTag(BlockTags.LOGS);
	}

	/**
	 * The shale family blocks of one shape.
	 *
	 * @param shape the block class, such as {@link StairBlock}
	 * @return the matching blocks
	 */
	static List<Block> shapes(Class<? extends Block> shape) {
		return ModBlocks.shaleFamily().stream().filter(shape::isInstance).toList();
	}

	private static void addAll(TagAppender<Block> tag, List<Block> blocks) {
		blocks.forEach(block -> tag.add(key(block)));
	}

	private static ResourceKey<Block> key(Block block) {
		return BuiltInRegistries.BLOCK.getResourceKey(block).orElseThrow();
	}
}
