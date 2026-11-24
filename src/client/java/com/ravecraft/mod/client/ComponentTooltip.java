package com.ravecraft.mod.client;

import com.ravecraft.mod.item.ItemOrigins;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Shows the part a mold makes as a gray line under its tooltip (spec 6.3). */
public final class ComponentTooltip {
	private ComponentTooltip() {
	}

	/** Adds the "For the extruder" line to the tooltips of molds. */
	public static void register() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			ItemOrigins.moldFor(stack.getItem()).ifPresent(product -> lines.add(
					Component.translatable("tooltip.ravecraft.mold_for", new ItemStack(product).getHoverName())
							.withStyle(ChatFormatting.GRAY)));
		});
	}
}
