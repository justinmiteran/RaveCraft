package com.ravecraft.mod.client.options;

import java.util.List;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;

/**
 * The player's own RaveCraft sound settings, shown in Options › Music &amp; Sounds and saved in {@code options.txt}.
 * They change what this player hears, never the range the server relays.
 */
public final class RaveCraftOptions {
	private static final OptionInstance<Double> VOLUME = new OptionInstance<>(
			"options.ravecraft.volume",
			OptionInstance.noTooltip(),
			(caption, value) -> Component.translatable("options.percent_value", caption, (int) (value * 100.0)),
			OptionInstance.UnitDouble.INSTANCE,
			1.0,
			value -> {
			});
	private static final OptionInstance<Integer> MAX_SOURCES = new OptionInstance<>(
			"options.ravecraft.maxSources",
			OptionInstance.noTooltip(),
			Options::genericValueLabel,
			new OptionInstance.IntRange(2, 32),
			8,
			value -> {
			});
	private static final OptionInstance<Boolean> ACOUSTIC_EFFECTS = OptionInstance.createBoolean(
			"options.ravecraft.acousticEffects",
			OptionInstance.cachedConstantTooltip(Component.translatable("options.ravecraft.acousticEffects.tooltip")),
			true);
	private static final OptionInstance<OcclusionQuality> OCCLUSION_QUALITY = new OptionInstance<>(
			"options.ravecraft.occlusionQuality",
			OptionInstance.cachedConstantTooltip(Component.translatable("options.ravecraft.occlusionQuality.tooltip")),
			(caption, value) -> value.caption(),
			new OptionInstance.Enum<>(List.of(OcclusionQuality.values()), OcclusionQuality.CODEC),
			OcclusionQuality.MEDIUM,
			value -> {
			});

	private RaveCraftOptions() {
	}

	/** RaveCraft volume, 0 to 1, on top of the master and "Jukebox/Note Blocks" volumes. */
	public static OptionInstance<Double> volume() {
		return VOLUME;
	}

	/** How many speakers this player hears at once (2 to 32). */
	public static OptionInstance<Integer> maxSources() {
		return MAX_SOURCES;
	}

	/** Whether the EFX filter is used: air absorption and treble muffled through blocks. */
	public static OptionInstance<Boolean> acousticEffects() {
		return ACOUSTIC_EFFECTS;
	}

	/** How much tracing is spent per tick finding how sound gets through and around blocks. */
	public static OptionInstance<OcclusionQuality> occlusionQuality() {
		return OCCLUSION_QUALITY;
	}

	/** Loads or saves the options, from the {@code Options.processOptions} mixin. */
	public static void process(Options.FieldAccess access) {
		access.process("ravecraft_volume", VOLUME);
		access.process("ravecraft_maxSources", MAX_SOURCES);
		access.process("ravecraft_efx", ACOUSTIC_EFFECTS);
		access.process("ravecraft_occlusionQuality", OCCLUSION_QUALITY);
	}
}
