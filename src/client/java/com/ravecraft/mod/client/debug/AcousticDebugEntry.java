package com.ravecraft.mod.client.debug;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.client.audio.render.AcousticDebugInfo;
import com.ravecraft.mod.client.audio.render.ReverbScheduler;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.debug.DebugEntryCategory;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/**
 * A debug screen entry of the acoustic engine. Off by default, like every added entry; each one is turned on from the
 * debug options screen (F3 + F6), where they are listed together under a "RaveCraft" heading.
 */
public final class AcousticDebugEntry implements DebugScreenEntry {
	/** Sorted after vanilla's text (1) and renderer (2) categories. */
	static final DebugEntryCategory CATEGORY = new DebugEntryCategory(
			Component.translatable("debug.options.category.ravecraft").withStyle(ChatFormatting.BOLD,
					ChatFormatting.UNDERLINE),
			3.0F);
	private final Identifier id;
	private final Supplier<List<String>> lines;

	private AcousticDebugEntry(Identifier id, Supplier<List<String>> lines) {
		this.id = id;
		this.lines = lines;
	}

	/** Registers "ravecraft:speakers", "listener", "reverb", "streams" and "perf". */
	public static void registerAll() {
		register("speakers", AcousticDebugInfo::speakers);
		register("listener", AcousticDebugInfo::listener);
		register("reverb", ReverbScheduler::debugLines);
		register("streams", AcousticDebugInfo::streams);
		register("perf", AcousticDebugInfo::perf);
	}

	private static void register(String path, Supplier<List<String>> lines) {
		Identifier id = RaveCraft.id(path);
		DebugScreenEntries.register(id, new AcousticDebugEntry(id, lines));
	}

	@Override
	public DebugEntryCategory category() {
		return CATEGORY;
	}

	@Override
	public void display(DebugScreenDisplayer displayer, @Nullable Level serverOrClientLevel,
			@Nullable LevelChunk clientChunk, @Nullable LevelChunk serverChunk) {
		displayer.addToGroup(id, lines.get());
	}
}
