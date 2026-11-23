package com.ravecraft.mod.world.gen;

import com.ravecraft.mod.RaveCraft;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.biome.v1.BiomeModificationContext;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
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

	private ModWorldgen() {
	}

	/**
	 * Adds the placed features to the biomes. Call once, after the blocks are registered.
	 *
	 * <p>Fabric applies modifications sorted by id, so all the ores go through a single modification whose
	 * additions keep this order: shale pockets first, so the oil shale veins placed afterwards cut through them.
	 */
	public static void init() {
		GenerationStep.Decoration ores = GenerationStep.Decoration.UNDERGROUND_ORES;
		// The biome tag, not foundInOverworld(): the latter depends on the world's biome source (flat worlds...).
		Predicate<BiomeSelectionContext> overworld = BiomeSelectors.tag(BiomeTags.IS_OVERWORLD);
		Predicate<BiomeSelectionContext> dry = BiomeSelectors.tag(ConventionalBiomeTags.IS_DESERT)
				.or(BiomeSelectors.tag(BiomeTags.IS_BADLANDS));
		BiomeModifications.create(RaveCraft.id("ores"))
				.add(ModificationPhase.ADDITIONS, overworld, context -> addOres(context, ores))
				.add(ModificationPhase.ADDITIONS, overworld.and(dry),
						context -> context.getGenerationSettings().addFeature(ores, PLACED_OIL_SHALE_EXTRA));
	}

	private static void addOres(BiomeModificationContext context, GenerationStep.Decoration step) {
		BiomeModificationContext.GenerationSettingsContext settings = context.getGenerationSettings();
		settings.addFeature(step, PLACED_SHALE);
		settings.addFeature(step, PLACED_OIL_SHALE);
		settings.addFeature(step, PLACED_NEODYMIUM);
	}

	private static ResourceKey<Feature> feature(String name) {
		return ResourceKey.create(Registries.FEATURE, RaveCraft.id(name));
	}

	private static ResourceKey<PlacedFeature> placed(String name) {
		return ResourceKey.create(Registries.PLACED_FEATURE, RaveCraft.id(name));
	}
}
