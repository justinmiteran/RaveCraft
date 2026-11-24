package com.ravecraft.mod.registry;

import com.ravecraft.mod.RaveCraft;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/** Creative mode tabs. */
public final class ModCreativeTabs {
	public static final CreativeModeTab RAVECRAFT = Registry.register(
			BuiltInRegistries.CREATIVE_MODE_TAB, RaveCraft.id("main"),
			FabricCreativeModeTab.builder()
					.title(Component.translatable("itemGroup.ravecraft.main"))
					.icon(() -> new ItemStack(ModBlocks.PORTABLE_SPEAKER))
					.displayItems((parameters, output) -> {
						ModBlocks.ores().forEach(output::accept);
						ModBlocks.shaleFamily().forEach(output::accept);
						ModBlocks.storageBlocks().forEach(output::accept);
						ModItems.materials().forEach(output::accept);
						ModBlocks.speakers().forEach(output::accept);
					})
					.build());

	private ModCreativeTabs() {
	}

	/** Forces class loading, hence registration. */
	public static void init() {
	}
}
