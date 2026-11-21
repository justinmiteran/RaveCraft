package com.ravecraft.mod.datagen;

import com.ravecraft.mod.block.SpeakerBlock;
import com.ravecraft.mod.client.item.SpeakerPowered;
import com.ravecraft.mod.registry.ModBlocks;
import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.color.item.Dye;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.renderer.item.properties.select.DisplayContext;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.Block;

/**
 * Blockstates, block models, item models and {@code assets/<modid>/items/*.json}. Custom geometry: design it in
 * Blockbench ({@code art/models}), export the JSON to {@code assets/<modid>/models/block/} and reference it here.
 */
public class RaveCraftModelProvider extends FabricModelProvider {
	/** A provider writing to {@code output}. */
	public RaveCraftModelProvider(FabricPackOutput output) {
		super(output);
	}

	@Override
	public void generateBlockStateModels(BlockModelGenerators generators) {
		speaker(ModBlocks.PORTABLE_SPEAKER, generators);
		speaker(ModBlocks.PARTY_SPEAKER, generators);
	}

	/**
	 * The blockstate and item model definition of a speaker. Its block models are hand-written Blockbench exports
	 * ({@code models/block/<id>[_on].json}). The item shows a flat 2D icon in the inventory and the 3D block model
	 * everywhere else (hand, ground, item frame), like the trident. Both tint layer / tintindex 0 (the fabric or
	 * grille) with the item's dye colour, like leather armour, and light the power LED when the item carries
	 * powered settings.
	 */
	private static void speaker(Block block, BlockModelGenerators generators) {
		Identifier off = ModelLocationUtils.getModelLocation(block);
		Identifier on = ModelLocationUtils.getModelLocation(block, "_on");
		generators.blockStateOutput.accept(MultiVariantGenerator.dispatch(block)
				.with(BlockModelGenerators.createBooleanModelDispatch(SpeakerBlock.POWERED,
						BlockModelGenerators.plainVariant(on), BlockModelGenerators.plainVariant(off)))
				.with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
		Item speaker = block.asItem();
		Identifier icon = flatIcon(speaker, "", "_overlay", generators);
		Identifier iconOn = flatIcon(speaker, "_on", "_overlay_on", generators);
		// Item tints are ARGB: without an opaque alpha the undyed fabric would be invisible.
		Dye fabricTint = new Dye(ARGB.opaque(SpeakerBlock.DEFAULT_COLOR));
		generators.itemModelOutput.accept(speaker, ItemModelUtils.select(new DisplayContext(),
				powered(ItemModelUtils.tintedModel(on, fabricTint), ItemModelUtils.tintedModel(off, fabricTint)),
				ItemModelUtils.when(ItemDisplayContext.GUI,
						powered(ItemModelUtils.tintedModel(iconOn, fabricTint), ItemModelUtils.tintedModel(icon, fabricTint)))));
	}

	private static Identifier flatIcon(Item item, String suffix, String overlay, BlockModelGenerators generators) {
		return ModelTemplates.TWO_LAYERED_ITEM.create(ModelLocationUtils.getModelLocation(item, suffix),
				TextureMapping.layered(TextureMapping.getItemTexture(item), TextureMapping.getItemTexture(item, overlay)),
				generators.modelOutput);
	}

	private static ItemModel.Unbaked powered(ItemModel.Unbaked onModel, ItemModel.Unbaked offModel) {
		return ItemModelUtils.conditional(new SpeakerPowered(), onModel, offModel);
	}

	@Override
	public void generateItemModels(ItemModelGenerators generators) {
	}
}
