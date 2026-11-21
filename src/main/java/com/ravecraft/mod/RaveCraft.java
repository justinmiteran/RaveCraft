package com.ravecraft.mod;

import com.ravecraft.mod.config.AcousticConfig;
import com.ravecraft.mod.network.AudioRelay;
import com.ravecraft.mod.network.SpeakerNetwork;
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

/** The mod's entry point: registers its content, payloads, config and server handlers. */
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
		AcousticConfig.init();
		AudioRelay.init();
		SpeakerNetwork.init();
		LOGGER.info("RaveCraft initialized");
	}

	/** An identifier in the mod's namespace. */
	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
