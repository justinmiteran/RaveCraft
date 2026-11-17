package com.ravecraft.mod.client;

import com.ravecraft.mod.block.SpeakerScreenOpener;
import com.ravecraft.mod.client.audio.LocalPlayback;
import com.ravecraft.mod.client.audio.stream.ClientRelay;
import com.ravecraft.mod.client.item.SpeakerPowered;
import com.ravecraft.mod.client.screen.SpeakerBrowserScreen;
import com.ravecraft.mod.registry.ModBlocks;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.minecraft.client.Minecraft;

/** Client-only setup: renderers, screens, key mappings, HUD. Never referenced from {@code src/main}. */
public class RaveCraftClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		SpeakerPowered.register();
		LocalPlayback.register();
		ClientRelay.init();
		SpeakerScreenOpener.set(pos -> Minecraft.getInstance().gui.setScreen(new SpeakerBrowserScreen(pos)));
		BlockColorRegistry.register(List.of(new SpeakerFabricTint()), ModBlocks.PORTABLE_SPEAKER);
	}
}
