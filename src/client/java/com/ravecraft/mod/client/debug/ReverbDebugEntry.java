package com.ravecraft.mod.client.debug;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.client.audio.render.ReverbScheduler;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/**
 * The "ravecraft:reverb" debug screen entry: what the reverb measured around the listener. Off by default, like every
 * added entry; turned on from the debug options screen (F3 + F6).
 */
public final class ReverbDebugEntry implements DebugScreenEntry {
	public static final Identifier ID = RaveCraft.id("reverb");

	public static void register() {
		DebugScreenEntries.register(ID, new ReverbDebugEntry());
	}

	@Override
	public void display(DebugScreenDisplayer displayer, @Nullable Level serverOrClientLevel,
			@Nullable LevelChunk clientChunk, @Nullable LevelChunk serverChunk) {
		displayer.addToGroup(ID, ReverbScheduler.debugLines());
	}
}
