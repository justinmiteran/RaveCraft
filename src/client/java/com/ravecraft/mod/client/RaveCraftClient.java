package com.ravecraft.mod.client;

import com.ravecraft.mod.client.audio.render.AcousticRenderer;
import com.ravecraft.mod.client.audio.render.AcousticSettingsClient;
import com.ravecraft.mod.client.audio.render.OcclusionScheduler;
import com.ravecraft.mod.client.audio.render.ReverbScheduler;
import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.block.SpeakerScreenOpener;
import com.ravecraft.mod.client.audio.LocalPlayback;
import com.ravecraft.mod.client.audio.stream.ClientRelay;
import com.ravecraft.mod.client.debug.AcousticDebugEntry;
import com.ravecraft.mod.client.item.SpeakerPowered;
import com.ravecraft.mod.client.screen.SpeakerScreen;
import com.ravecraft.mod.registry.ModBlocks;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.minecraft.client.Minecraft;

/** Client-only setup: renderers, screens, key mappings, HUD. Never referenced from {@code src/main}. */
public class RaveCraftClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		SpeakerPowered.register();
		LocalPlayback.register();
		ClientRelay.init();
		AcousticSettingsClient.register();
		AcousticRenderer.register();
		OcclusionScheduler.register();
		ReverbScheduler.register();
		AcousticDebugEntry.registerAll();
		if (FabricLoader.getInstance().isModLoaded("sound_physics_remastered")) {
			RaveCraft.LOGGER.info("Sound Physics Remastered is loaded: RaveCraft speakers keep their own occlusion");
		}
		SpeakerTooltip.register();
		SpeakerScreenOpener.set(pos -> Minecraft.getInstance().gui.setScreen(new SpeakerScreen(pos)));
		BlockColorRegistry.register(List.of(new SpeakerFabricTint()), ModBlocks.PORTABLE_SPEAKER);
	}
}
