package com.ravecraft.mod.compat.trinkets;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ravecraft.mod.client.render.BackSpeakerRenderer;
import com.ravecraft.mod.registry.ModBlocks;
import eu.pb4.trinkets.api.TrinketSlotAccess;
import eu.pb4.trinkets.api.client.TrinketRenderer;
import eu.pb4.trinkets.api.client.TrinketRendererRegistry;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.item.ItemStack;

/**
 * Draws a party speaker worn in a Trinkets slot on the wearer's back, as {@link BackSpeakerRenderer} does for the chest
 * slot. Only loaded when Trinkets is, from a guarded call; the API is described on {@link TrinketsCompat}.
 */
public final class TrinketsClientCompat {
	private TrinketsClientCompat() {
	}

	/** Registers the party speaker's trinket renderer. */
	public static void init() {
		TrinketRendererRegistry.registerRenderer(ModBlocks.PARTY_SPEAKER.asItem(),
				context -> new BackTrinketRenderer(BackSpeakerRenderer.create(context.getBlockModelResolver())));
	}

	/** Hands a worn speaker to the chest slot's drawing, on humanoid models only. */
	private record BackTrinketRenderer(BackSpeakerRenderer drawer) implements TrinketRenderer {
		@Override
		public void submit(ItemStack stack, TrinketSlotAccess slot, EntityModel<? extends LivingEntityRenderState> model,
				PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int light, LivingEntityRenderState state,
				float limbAngle, float limbDistance) {
			if (model instanceof HumanoidModel<?> humanoid) {
				drawer.submitOnBack(poseStack, submitNodeCollector, stack, humanoid.body, light, state.outlineColor);
			}
		}
	}
}
