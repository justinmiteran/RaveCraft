package com.ravecraft.mod.client;

import com.ravecraft.mod.item.ItemOrigins;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Shows the machine that makes a part, and the part a mold makes, as a gray line under the tooltip (spec 6.3). */
public final class ComponentTooltip {
	private ComponentTooltip() {
	}

	/** Adds the "Made in" and "For the extruder" lines to the tooltips of machine products and molds. */
	public static void register() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			ItemOrigins.madeIn(stack.getItem()).ifPresent(machine -> lines.add(
					Component.translatable("tooltip.ravecraft.made_in", machine.getName())
							.withStyle(ChatFormatting.GRAY)));
			ItemOrigins.moldFor(stack.getItem()).ifPresent(product -> lines.add(
					Component.translatable("tooltip.ravecraft.mold_for", new ItemStack(product).getHoverName())
							.withStyle(ChatFormatting.GRAY)));
		});
	}
}
