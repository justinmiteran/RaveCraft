package com.ravecraft.mod.datagen;

import com.ravecraft.mod.registry.ModBlocks;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootSubProvider;
import net.minecraft.core.HolderLookup;

public class RaveCraftBlockLootProvider extends FabricBlockLootSubProvider {
	public RaveCraftBlockLootProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
		super(output, registries);
	}

	@Override
	public void generate() {
		dropSelf(ModBlocks.EXAMPLE_BLOCK);
	}
}
