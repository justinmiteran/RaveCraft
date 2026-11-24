package com.ravecraft.mod.datagen;

import com.ravecraft.mod.core.world.OreVein;
import com.ravecraft.mod.core.world.OreVeins;
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
				new OreFeature(naturalStone, ModBlocks.SHALE.defaultBlockState(), OreVeins.SHALE.size()));
		context.register(ModWorldgen.ORE_OIL_SHALE, new OreFeature(List.of(
				BlockReplacement.replace(stoneOreReplaceables, ModBlocks.OIL_SHALE.defaultBlockState()),
				BlockReplacement.replace(new BlockMatchTest(ModBlocks.SHALE), ModBlocks.OIL_SHALE.defaultBlockState())),
				OreVeins.OIL_SHALE.size()));
		context.register(ModWorldgen.ORE_NEODYMIUM, new OreFeature(List.of(BlockReplacement.replace(
				deepslateOreReplaceables, ModBlocks.DEEPSLATE_NEODYMIUM_ORE.defaultBlockState())),
				OreVeins.NEODYMIUM.size(), OreVeins.NEODYMIUM_AIR_DISCARD));
	}

	/**
	 * Registers the placements.
	 *
	 * @param context the registry being built
	 */
	static void placements(BootstrapContext<PlacedFeature> context) {
		HolderGetter<Feature> features = context.lookup(Registries.FEATURE);
		PlacementModifier oilShaleHeight = height(OreVeins.OIL_SHALE);
		PlacementUtils.register(context, ModWorldgen.PLACED_SHALE, features.getOrThrow(ModWorldgen.ORE_SHALE),
				common(OreVeins.SHALE.count(), height(OreVeins.SHALE)));
		PlacementUtils.register(context, ModWorldgen.PLACED_OIL_SHALE, features.getOrThrow(ModWorldgen.ORE_OIL_SHALE),
				common(OreVeins.OIL_SHALE.count(), oilShaleHeight));
		PlacementUtils.register(context, ModWorldgen.PLACED_OIL_SHALE_EXTRA,
				features.getOrThrow(ModWorldgen.ORE_OIL_SHALE), common(OreVeins.OIL_SHALE_DRY_COUNT, oilShaleHeight));
		PlacementUtils.register(context, ModWorldgen.PLACED_NEODYMIUM, features.getOrThrow(ModWorldgen.ORE_NEODYMIUM),
				common(OreVeins.NEODYMIUM.count(), height(OreVeins.NEODYMIUM)));
	}

	/** The height range of a vein: a trapezoid (vanilla's triangle) or uniform. */
	private static PlacementModifier height(OreVein vein) {
		VerticalAnchor bottom = VerticalAnchor.absolute(vein.minY());
		VerticalAnchor top = VerticalAnchor.absolute(vein.maxY());
		return vein.triangle() ? HeightRangePlacement.triangle(bottom, top) : HeightRangePlacement.uniform(bottom, top);
	}

	/** Like vanilla {@code commonOrePlacement}: a fixed count of veins per chunk, spread, at a height, per biome. */
	private static List<PlacementModifier> common(int count, PlacementModifier height) {
		return List.of(CountPlacement.of(count), InSquarePlacement.spread(), height, BiomeFilter.biome());
	}
}
