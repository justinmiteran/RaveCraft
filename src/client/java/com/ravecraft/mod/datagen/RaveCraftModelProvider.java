package com.ravecraft.mod.datagen;

import com.ravecraft.mod.block.PortableSpeakerBlock;
import com.ravecraft.mod.registry.ModBlocks;
import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.color.item.Dye;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.renderer.item.properties.select.DisplayContext;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;

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
		// Hand-written Blockbench models (models/block/portable_speaker[_on].json): only the blockstate and the
		// item model definition are generated here.
		Identifier off = ModelLocationUtils.getModelLocation(ModBlocks.PORTABLE_SPEAKER);
		Identifier on = ModelLocationUtils.getModelLocation(ModBlocks.PORTABLE_SPEAKER, "_on");
		generators.blockStateOutput.accept(MultiVariantGenerator.dispatch(ModBlocks.PORTABLE_SPEAKER)
				.with(BlockModelGenerators.createBooleanModelDispatch(PortableSpeakerBlock.POWERED,
						BlockModelGenerators.plainVariant(on), BlockModelGenerators.plainVariant(off)))
				.with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
		// Flat 2D icon in the inventory, the 3D block model everywhere else (hand, ground, item frame), like the
		// trident. Both tint their fabric (layer / tintindex 0) with the item's dye colour, like leather armour.
		Item speaker = ModBlocks.PORTABLE_SPEAKER.asItem();
		Identifier icon = ModelTemplates.TWO_LAYERED_ITEM.create(ModelLocationUtils.getModelLocation(speaker),
				TextureMapping.layered(TextureMapping.getItemTexture(speaker), TextureMapping.getItemTexture(speaker, "_overlay")),
				generators.modelOutput);
		Dye fabricTint = new Dye(PortableSpeakerBlock.DEFAULT_COLOR);
		generators.itemModelOutput.accept(speaker, ItemModelUtils.select(new DisplayContext(),
				ItemModelUtils.tintedModel(off, fabricTint),
				ItemModelUtils.when(ItemDisplayContext.GUI, ItemModelUtils.tintedModel(icon, fabricTint))));
	}

	@Override
	public void generateItemModels(ItemModelGenerators generators) {
	}
}
