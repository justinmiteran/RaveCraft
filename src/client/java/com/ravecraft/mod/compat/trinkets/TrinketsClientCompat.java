package com.ravecraft.mod.compat.trinkets;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ravecraft.mod.RaveCraft;
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

	/**
	 * Hands a worn speaker to the chest slot's drawing, on humanoid models only. An incompatible Trinkets version fails
	 * at the first draw: drawing stops there, logged once, instead of crashing the game.
	 */
	private static final class BackTrinketRenderer implements TrinketRenderer {
		private final BackSpeakerRenderer drawer;
		private boolean broken;

		BackTrinketRenderer(BackSpeakerRenderer drawer) {
			this.drawer = drawer;
		}

		@Override
		public void submit(ItemStack stack, TrinketSlotAccess slot, EntityModel<? extends LivingEntityRenderState> model,
				PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int light, LivingEntityRenderState state,
				float limbAngle, float limbDistance) {
			if (broken || !(model instanceof HumanoidModel<?> humanoid)) {
				return;
			}
			try {
				drawer.submitOnBack(poseStack, submitNodeCollector, stack, humanoid.body, light, state.outlineColor);
			} catch (LinkageError e) {
				broken = true;
				RaveCraft.LOGGER.warn("Trinkets back render disabled: incompatible Trinkets version", e);
			}
		}
	}
}
