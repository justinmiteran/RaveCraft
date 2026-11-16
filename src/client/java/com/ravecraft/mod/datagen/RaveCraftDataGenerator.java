package com.ravecraft.mod.datagen;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

/**
 * Run with {@code ./gradlew runDatagen}. Output goes to {@code src/main/generated} (never edit it by hand).
 * Translations are hand-written in {@code assets/ravecraft/lang}.
 */
public class RaveCraftDataGenerator implements DataGeneratorEntrypoint {
	@Override
	public void onInitializeDataGenerator(FabricDataGenerator generator) {
		FabricDataGenerator.Pack pack = generator.createPack();
		pack.addProvider(RaveCraftModelProvider::new);
		pack.addProvider(RaveCraftBlockLootProvider::new);
		pack.addProvider(RaveCraftBlockTagProvider::new);
		pack.addProvider(RaveCraftRecipeProvider::new);
	}
}
