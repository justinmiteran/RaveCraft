package com.ravecraft.mod.datagen;

import com.ravecraft.mod.registry.ModBlocks;
import com.ravecraft.mod.registry.ModItems;
import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.model.ModelTemplates;

/**
 * Blockstates, block models, item models and {@code assets/<modid>/items/*.json}. Custom geometry: design it in
 * Blockbench ({@code art/models}), export the JSON to {@code assets/<modid>/models/block/} and reference it here.
 */
public class RaveCraftModelProvider extends FabricModelProvider {
	public RaveCraftModelProvider(FabricPackOutput output) {
		super(output);
	}

	@Override
	public void generateBlockStateModels(BlockModelGenerators generators) {
		// Uses textures/block/example_block.png on all six faces.
		generators.createTrivialCube(ModBlocks.EXAMPLE_BLOCK);
	}

	@Override
	public void generateItemModels(ItemModelGenerators generators) {
		generators.generateFlatItem(ModItems.EXAMPLE_ITEM, ModelTemplates.FLAT_ITEM);
	}
}
