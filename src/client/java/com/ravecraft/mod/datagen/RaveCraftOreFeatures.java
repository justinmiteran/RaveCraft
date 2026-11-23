package com.ravecraft.mod.datagen;

import com.ravecraft.mod.registry.ModBlocks;
import com.ravecraft.mod.world.gen.ModWorldgen;
import java.util.List;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.feature.BlockReplacement;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.HeightMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;

/** Ore and rock features and their placements, after vanilla {@code OreFeatures} and {@code OrePlacements}. */
final class RaveCraftOreFeatures {
	private static final int SHALE_SIZE = 64;
	private static final int SHALE_COUNT = 2;
	private static final int SHALE_MAX_Y = 80;
	private static final int OIL_SHALE_SIZE = 17;
	private static final int OIL_SHALE_COUNT = 12;
	private static final int OIL_SHALE_EXTRA_COUNT = 8;
	private static final int OIL_SHALE_MIN_Y = 20;
	private static final int OIL_SHALE_MAX_Y = 80;
	private static final int NEODYMIUM_SIZE = 6;
	private static final float NEODYMIUM_AIR_DISCARD = 0.5F;
	private static final int NEODYMIUM_COUNT = 3;
	private static final int NEODYMIUM_MIN_Y = -64;
	private static final int NEODYMIUM_MAX_Y = -16;
	private static final int DEEPSLATE_TOP_Y = 8;

	private RaveCraftOreFeatures() {
	}

	/**
	 * Registers the features, with the same rule tests as vanilla ores.
	 *
	 * @param context the registry being built
	 */
	static void features(BootstrapContext<Feature> context) {
		RuleTest naturalStone = new TagMatchTest(BlockTags.BASE_STONE_OVERWORLD);
		RuleTest stoneOreReplaceables = RuleTest.either(new TagMatchTest(BlockTags.HEIGHT_SPECIFIC_ORE_REPLACEABLES),
				HeightMatchTest.min(0), new TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES));
		RuleTest deepslateOreReplaceables = RuleTest.either(
				new TagMatchTest(BlockTags.HEIGHT_SPECIFIC_ORE_REPLACEABLES), HeightMatchTest.max(DEEPSLATE_TOP_Y),
				new TagMatchTest(BlockTags.DEEPSLATE_ORE_REPLACEABLES));
		context.register(ModWorldgen.ORE_SHALE,
				new OreFeature(naturalStone, ModBlocks.SHALE.defaultBlockState(), SHALE_SIZE));
		context.register(ModWorldgen.ORE_OIL_SHALE, new OreFeature(List.of(
				BlockReplacement.replace(stoneOreReplaceables, ModBlocks.OIL_SHALE.defaultBlockState()),
				BlockReplacement.replace(new BlockMatchTest(ModBlocks.SHALE), ModBlocks.OIL_SHALE.defaultBlockState())),
				OIL_SHALE_SIZE));
		context.register(ModWorldgen.ORE_NEODYMIUM, new OreFeature(List.of(BlockReplacement.replace(
				deepslateOreReplaceables, ModBlocks.DEEPSLATE_NEODYMIUM_ORE.defaultBlockState())),
				NEODYMIUM_SIZE, NEODYMIUM_AIR_DISCARD));
	}

	/**
	 * Registers the placements.
	 *
	 * @param context the registry being built
	 */
	static void placements(BootstrapContext<PlacedFeature> context) {
		HolderGetter<Feature> features = context.lookup(Registries.FEATURE);
		PlacementModifier oilShaleHeight = HeightRangePlacement.uniform(VerticalAnchor.absolute(OIL_SHALE_MIN_Y),
				VerticalAnchor.absolute(OIL_SHALE_MAX_Y));
		PlacementUtils.register(context, ModWorldgen.PLACED_SHALE, features.getOrThrow(ModWorldgen.ORE_SHALE),
				common(SHALE_COUNT, HeightRangePlacement.uniform(VerticalAnchor.absolute(0),
						VerticalAnchor.absolute(SHALE_MAX_Y))));
		PlacementUtils.register(context, ModWorldgen.PLACED_OIL_SHALE, features.getOrThrow(ModWorldgen.ORE_OIL_SHALE),
				common(OIL_SHALE_COUNT, oilShaleHeight));
		PlacementUtils.register(context, ModWorldgen.PLACED_OIL_SHALE_EXTRA,
				features.getOrThrow(ModWorldgen.ORE_OIL_SHALE), common(OIL_SHALE_EXTRA_COUNT, oilShaleHeight));
		PlacementUtils.register(context, ModWorldgen.PLACED_NEODYMIUM, features.getOrThrow(ModWorldgen.ORE_NEODYMIUM),
				common(NEODYMIUM_COUNT, HeightRangePlacement.triangle(VerticalAnchor.absolute(NEODYMIUM_MIN_Y),
						VerticalAnchor.absolute(NEODYMIUM_MAX_Y))));
	}

	/** Like vanilla {@code commonOrePlacement}: a fixed count of veins per chunk, spread, at a height, per biome. */
	private static List<PlacementModifier> common(int count, PlacementModifier height) {
		return List.of(CountPlacement.of(count), InSquarePlacement.spread(), height, BiomeFilter.biome());
	}
}
