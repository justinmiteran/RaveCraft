package com.ravecraft.mod.client;

import com.ravecraft.mod.client.item.SpeakerPowered;
import com.ravecraft.mod.registry.ModBlocks;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;

/** Client-only setup: renderers, screens, key mappings, HUD. Never referenced from {@code src/main}. */
public class RaveCraftClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		SpeakerPowered.register();
		BlockColorRegistry.register(List.of(new SpeakerFabricTint()), ModBlocks.PORTABLE_SPEAKER);
	}
}
