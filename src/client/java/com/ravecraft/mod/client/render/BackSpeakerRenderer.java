package com.ravecraft.mod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ravecraft.mod.block.SpeakerBlock;
import com.ravecraft.mod.component.SpeakerData;
import com.ravecraft.mod.registry.ModBlocks;
import com.ravecraft.mod.registry.ModDataComponents;
import it.unimi.dsi.fastutil.ints.IntList;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRenderEvents;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws a party speaker worn in the chest slot on the wearer's back: the block model at 0.6 scale, grille facing out
 * of the back, with the stack's dye on the grille and its LED lit when powered. Registered as a Fabric armor renderer:
 * the speaker has no armor model, and only an armor renderer makes vanilla keep the chest stack in the render state.
 */
public final class BackSpeakerRenderer implements ArmorRenderer {
	private static final BlockDisplayContext DISPLAY_CONTEXT = BlockDisplayContext.create();
	private static final float SCALE = 0.6F;
	/** Centre of the torso, below the neck (model space, y down): 6 of its 12 pixels. */
	private static final float BACK_Y = 6.0F / 16.0F;
	/** The back surface of the torso (2 px behind its centre) plus half the speaker's depth (6 px) once scaled. */
	private static final float BACK_Z = 2.0F / 16.0F + 6.0F / 16.0F * SCALE;
	/** Centre of the speaker's outer box in block space: x 1..15, y 0..20, z 2..14 pixels. */
	private static final float MODEL_CENTRE_X = 0.5F;
	private static final float MODEL_CENTRE_Y = 10.0F / 16.0F;
	private static final float MODEL_CENTRE_Z = 0.5F;
	/** Tint layer of the grille, as {@link com.ravecraft.mod.client.SpeakerFabricTint}. */
	private static final int GRILLE_TINT = 0;

	private final BlockModelResolver resolver;
	/** Scratch state, refilled for each submit: the collector copies the model parts and tints it is handed. */
	private final BlockModelRenderState model = new BlockModelRenderState();

	private BackSpeakerRenderer(BlockModelResolver resolver) {
		this.resolver = resolver;
	}

	/** A renderer drawing with {@code resolver}; also used for the Trinkets back slot. */
	public static BackSpeakerRenderer create(BlockModelResolver resolver) {
		return new BackSpeakerRenderer(resolver);
	}

	/** Registers the renderer for the party speaker, and hides the cape it would cover. */
	public static void register() {
		ArmorRenderer.register(context -> new BackSpeakerRenderer(context.getBlockModelResolver()), ModBlocks.PARTY_SPEAKER);
		LivingEntityFeatureRenderEvents.ALLOW_CAPE_RENDER.register(state -> !state.chestEquipment.is(ModBlocks.PARTY_SPEAKER.asItem()));
	}

	/** Follows the body, moves to the back and draws the block model facing out of it. */
	@Override
	public void render(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, ItemStack stack,
			HumanoidRenderState humanoidRenderState, EquipmentSlot slot, int light, HumanoidModel<HumanoidRenderState> contextModel) {
		if (slot != EquipmentSlot.CHEST) {
			return;
		}
		submitOnBack(poseStack, submitNodeCollector, stack, contextModel.body, light, humanoidRenderState.outlineColor);
	}

	/** Draws {@code stack} on the back of {@code body}, at the current pose of the wearer's model. */
	public void submitOnBack(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, ItemStack stack, ModelPart body,
			int light, int outlineColor) {
		resolver.update(model, stateOf(stack), DISPLAY_CONTEXT);
		IntList tints = model.tintLayers();
		if (tints.size() > GRILLE_TINT) {
			tints.set(GRILLE_TINT, grilleColor(stack));
		}
		poseStack.pushPose();
		body.translateAndRotate(poseStack);
		poseStack.translate(0.0F, BACK_Y, BACK_Z);
		// Flipping x and y turns the y-down model space into the block's y-up space (a half turn about z, not a
		// mirror); the south-facing state then shows its grille towards +z, out of the back.
		poseStack.scale(-SCALE, -SCALE, SCALE);
		poseStack.translate(-MODEL_CENTRE_X, -MODEL_CENTRE_Y, -MODEL_CENTRE_Z);
		model.submit(poseStack, submitNodeCollector, light, OverlayTexture.NO_OVERLAY, outlineColor);
		poseStack.popPose();
	}

	/** The party speaker block facing south, its LED lit when the stack's settings are powered. */
	private static BlockState stateOf(ItemStack stack) {
		SpeakerData data = stack.get(ModDataComponents.SPEAKER);
		return ModBlocks.PARTY_SPEAKER.defaultBlockState()
				.setValue(SpeakerBlock.FACING, Direction.SOUTH)
				.setValue(SpeakerBlock.POWERED, data != null && data.settings().powered());
	}

	private static int grilleColor(ItemStack stack) {
		DyedItemColor dye = stack.get(DataComponents.DYED_COLOR);
		return ARGB.opaque(dye != null ? dye.rgb() : SpeakerBlock.DEFAULT_COLOR);
	}
}
