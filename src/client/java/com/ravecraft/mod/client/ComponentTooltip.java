package com.ravecraft.mod.client;

import com.ravecraft.mod.registry.ModTags;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Shows the machine a mold goes in as a gray line under its tooltip (spec 6.3). */
public final class ComponentTooltip {
	private ComponentTooltip() {
	}

	/** Adds the "For the extruder" line to the tooltips of molds. */
	public static void register() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (stack.is(ModTags.Items.MOLDS)) {
				lines.add(Component.translatable("tooltip.ravecraft.mold").withStyle(ChatFormatting.GRAY));
			}
		});
	}
}
