package com.ravecraft.mod.client.debug;

import com.mojang.blaze3d.platform.InputConstants;
import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.core.acoustics.debug.view.SoundViewMode;
import java.util.Locale;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugEntryCategory;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.client.gui.components.debug.DebugScreenEntryList;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/**
 * The two layers of the in-world sound view, as debug entries listed under "RaveCraft" in the debug options (F3 + F6),
 * and the F3 + U key that cycles through them: none, paths, rooms, both.
 */
public final class SoundViewEntries {
	/** The direct paths, the blocks they lose in and the detours. */
	public static final Identifier PATHS = RaveCraft.id("sound_paths");
	/** The room probes: rays, room summary and echo. */
	public static final Identifier ROOMS = RaveCraft.id("sound_rooms");

	private static final KeyMapping KEY = new KeyMapping("key.ravecraft.debug.soundView", InputConstants.Type.KEYBOARD,
			InputConstants.KEY_U, KeyMapping.Category.DEBUG);

	private SoundViewEntries() {
	}

	/** The F3 + U key. */
	public static KeyMapping key() {
		return KEY;
	}

	/** Registers both entries and the key. */
	public static void register() {
		DebugScreenEntries.register(PATHS, new Layer());
		DebugScreenEntries.register(ROOMS, new Layer());
		KeyMappingHelper.registerKeyMapping(KEY);
	}

	/** The layers shown now. */
	public static SoundViewMode mode() {
		DebugScreenEntryList entries = Minecraft.getInstance().debugEntries;
		return SoundViewMode.of(entries.isCurrentlyEnabled(PATHS), entries.isCurrentlyEnabled(ROOMS));
	}

	/** Moves to the next mode of the cycle and returns the message saying which one it is. */
	public static Component cycle() {
		SoundViewMode current = mode();
		SoundViewMode next = current.next();
		DebugScreenEntryList entries = Minecraft.getInstance().debugEntries;
		if (current.paths() != next.paths()) {
			entries.setStatus(PATHS, status(entries, next.paths()));
		}
		if (current.rooms() != next.rooms()) {
			entries.setStatus(ROOMS, status(entries, next.rooms()));
		}
		return Component.translatable("debug.ravecraft.sound_view." + next.name().toLowerCase(Locale.ROOT));
	}

	/** On as vanilla's {@code toggleStatus} turns an entry on: with the overlay when it is shown, else always. */
	private static DebugScreenEntryStatus status(DebugScreenEntryList entries, boolean on) {
		if (!on) {
			return DebugScreenEntryStatus.NEVER;
		}
		return entries.isOverlayVisible() ? DebugScreenEntryStatus.IN_OVERLAY : DebugScreenEntryStatus.ALWAYS_ON;
	}

	/** An entry with no text: it only switches its layer on. */
	private static final class Layer implements DebugScreenEntry {
		@Override
		public DebugEntryCategory category() {
			return AcousticDebugEntry.CATEGORY;
		}

		@Override
		public void display(DebugScreenDisplayer displayer, @Nullable Level serverOrClientLevel,
				@Nullable LevelChunk clientChunk, @Nullable LevelChunk serverChunk) {
		}
	}
}
