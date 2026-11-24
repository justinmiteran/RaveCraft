package com.ravecraft.mod.client.audio;

import com.ravecraft.mod.block.MachineBlock;
import com.ravecraft.mod.block.MachineSounds;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Plays one loop per working machine: started the first tick a machine works, and again whenever the sound engine
 * dropped it (sounds reloaded, sound volume back from zero), until the loop stops on its own ({@link MachineLoopSound}).
 * Client thread only.
 */
public final class MachineLoopSounds {
	private static final Map<BlockPos, MachineLoopSound> PLAYING = new HashMap<>();

	private MachineLoopSounds() {
	}

	/** Installs the player for the machines' client ticker, and forgets every loop on disconnect. */
	public static void register() {
		MachineSounds.install(MachineLoopSounds::working);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> PLAYING.clear());
	}

	private static void working(Level level, BlockPos pos, MachineBlock machine) {
		SoundManager sounds = Minecraft.getInstance().getSoundManager();
		MachineLoopSound playing = PLAYING.get(pos);
		if (playing != null && sounds.isActive(playing)) {
			return;
		}
		BlockPos key = pos.immutable();
		MachineLoopSound sound = new MachineLoopSound(level, key, machine, () -> PLAYING.remove(key));
		PLAYING.put(key, sound);
		sounds.play(sound);
	}
}
