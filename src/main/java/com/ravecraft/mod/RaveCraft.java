package com.ravecraft.mod;

import com.ravecraft.mod.network.AudioRelay;
import com.ravecraft.mod.network.ModPayloads;
import com.ravecraft.mod.registry.ModBlockEntities;
import com.ravecraft.mod.registry.ModBlocks;
import com.ravecraft.mod.registry.ModCreativeTabs;
import com.ravecraft.mod.registry.ModDataComponents;
import com.ravecraft.mod.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RaveCraft implements ModInitializer {
	public static final String MOD_ID = "ravecraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// Order matters when registries reference each other (e.g. a creative tab references items).
		ModDataComponents.init();
		ModBlocks.init();
		ModBlockEntities.init();
		ModItems.init();
		ModCreativeTabs.init();
		ModPayloads.init();
		AudioRelay.init();
		LOGGER.info("RaveCraft initialized");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
