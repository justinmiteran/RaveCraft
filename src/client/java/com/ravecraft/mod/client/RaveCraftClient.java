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
import com.ravecraft.mod.client.debug.SoundViewEntries;
import com.ravecraft.mod.client.debug.SoundViewRenderer;
import com.ravecraft.mod.client.item.SpeakerPowered;
import com.ravecraft.mod.client.render.BackSpeakerRenderer;
import com.ravecraft.mod.client.screen.SpeakerScreen;
import com.ravecraft.mod.client.screen.machine.MachineScreen;
import com.ravecraft.mod.compat.trinkets.TrinketsClientCompat;
import com.ravecraft.mod.network.payload.SpeakerClosedPayload;
import com.ravecraft.mod.registry.ModBlocks;
import com.ravecraft.mod.registry.ModMenus;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.level.block.Block;

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
		SoundViewEntries.register();
		SoundViewRenderer.register();
		if (FabricLoader.getInstance().isModLoaded("sound_physics_remastered")) {
			RaveCraft.LOGGER.info("Sound Physics Remastered is loaded: RaveCraft speakers keep their own occlusion");
		}
		SpeakerTooltip.register();
		MenuScreens.register(ModMenus.MACHINE, MachineScreen::new);
		OpenSpeakerKey.register();
		SpeakerScreenOpener.set((speakerId, title) -> Minecraft.getInstance().gui.setScreen(new SpeakerScreen(speakerId, title)));
		// The server no longer lets this player control the speaker (gone, or out of reach): close its screen.
		ClientPlayNetworking.registerGlobalReceiver(SpeakerClosedPayload.TYPE, (payload, context) -> {
			if (context.client().gui.screen() instanceof SpeakerScreen screen && screen.speakerId().equals(payload.speakerId())) {
				screen.onClose();
			}
		});
		BlockColorRegistry.register(List.of(new SpeakerFabricTint()), ModBlocks.speakers().toArray(Block[]::new));
		BackSpeakerRenderer.register();
		if (FabricLoader.getInstance().isModLoaded("trinkets")) {
			initTrinkets();
		}
	}

	/** Draws the party speaker in the Trinkets back slot; on failure, only the chest slot one is drawn. */
	private static void initTrinkets() {
		try {
			TrinketsClientCompat.init();
		} catch (LinkageError | RuntimeException e) {
			RaveCraft.LOGGER.warn("Trinkets back render disabled, the chest slot one still draws", e);
		}
	}
}
