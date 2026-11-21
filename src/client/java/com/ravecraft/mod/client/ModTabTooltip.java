package com.ravecraft.mod.client;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.registry.ModCreativeTabs;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Adds the RaveCraft creative tab name (blue, under the item name) to creative tooltips that lack it. */
public final class ModTabTooltip {
	private ModTabTooltip() {
	}

	/** {@code lines} with the mod's tab name added, for an item of the mod. */
	public static List<Component> withModTab(ItemStack stack, List<Component> lines) {
		if (lines.isEmpty() || !RaveCraft.MOD_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace())) {
			return lines;
		}
		Component tabName = ModCreativeTabs.RAVECRAFT.getDisplayName();
		String text = tabName.getString();
		for (Component line : lines) {
			if (line.getString().equals(text)) {
				return lines;   // already added by vanilla (inventory and search tabs)
			}
		}
		List<Component> result = new ArrayList<>(lines);
		result.add(1, tabName.copy().withStyle(ChatFormatting.BLUE));
		return result;
	}
}
