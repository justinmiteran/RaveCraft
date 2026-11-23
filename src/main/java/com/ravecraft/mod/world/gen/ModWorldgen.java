package com.ravecraft.mod.world.gen;

import com.ravecraft.mod.RaveCraft;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Ore and rock generation keys. The features and placements themselves are generated as JSON by datagen
 * ({@code RaveCraftOreFeatures}); this class only adds the placed features to the overworld biomes.
 */
public final class ModWorldgen {
	public static final ResourceKey<Feature> ORE_SHALE = feature("ore_shale");
	public static final ResourceKey<Feature> ORE_OIL_SHALE = feature("ore_oil_shale");
	public static final ResourceKey<Feature> ORE_NEODYMIUM = feature("ore_neodymium");
	public static final ResourceKey<PlacedFeature> PLACED_SHALE = placed("ore_shale");
	public static final ResourceKey<PlacedFeature> PLACED_OIL_SHALE = placed("ore_oil_shale");
	public static final ResourceKey<PlacedFeature> PLACED_OIL_SHALE_EXTRA = placed("ore_oil_shale_extra");
	public static final ResourceKey<PlacedFeature> PLACED_NEODYMIUM = placed("ore_neodymium");

	/** Convention tag for deserts, filled by the Fabric convention tags. */
	private static final TagKey<Biome> IS_DESERT = TagKey.create(Registries.BIOME,
			Identifier.fromNamespaceAndPath("c", "is_desert"));

	private ModWorldgen() {
	}

	/** Adds the placed features to the biomes. Call once, after the blocks are registered. */
	public static void init() {
		GenerationStep.Decoration ores = GenerationStep.Decoration.UNDERGROUND_ORES;
		// The biome tag, not foundInOverworld(): the latter depends on the world's biome source (flat worlds...).
		Predicate<BiomeSelectionContext> overworld = BiomeSelectors.tag(BiomeTags.IS_OVERWORLD);
		// Shale first, so oil shale veins placed afterwards can cut through the shale pockets.
		BiomeModifications.addFeature(overworld, ores, PLACED_SHALE);
		BiomeModifications.addFeature(overworld, ores, PLACED_OIL_SHALE);
		BiomeModifications.addFeature(overworld.and(BiomeSelectors.tag(IS_DESERT)
				.or(BiomeSelectors.tag(BiomeTags.IS_BADLANDS))), ores, PLACED_OIL_SHALE_EXTRA);
		BiomeModifications.addFeature(overworld, ores, PLACED_NEODYMIUM);
	}

	private static ResourceKey<Feature> feature(String name) {
		return ResourceKey.create(Registries.FEATURE, RaveCraft.id(name));
	}

	private static ResourceKey<PlacedFeature> placed(String name) {
		return ResourceKey.create(Registries.PLACED_FEATURE, RaveCraft.id(name));
	}
}
