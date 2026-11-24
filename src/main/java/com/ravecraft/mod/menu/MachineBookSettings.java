package com.ravecraft.mod.menu;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.stats.RecipeBookSettings;
import net.minecraft.stats.RecipeBookSettings.TypeSettings;

/**
 * The machine book's settings carried by every {@link RecipeBookSettings} (implemented by
 * {@code RecipeBookSettingsMixin}), and the codecs that save and send them after the vanilla ones.
 */
public interface MachineBookSettings {
	/** Saved key of the machine book's open state. */
	String OPEN_KEY = "ravecraftIsMachineGuiOpen";

	/** Saved key of the machine book's filter. */
	String FILTERING_KEY = "ravecraftIsMachineFilteringCraftable";

	/**
	 * Returns the machine book's settings.
	 *
	 * @return whether the machine book is open and filtered
	 */
	TypeSettings ravecraft$machine();

	/**
	 * Replaces the machine book's settings.
	 *
	 * @param settings the new settings
	 */
	void ravecraft$setMachine(TypeSettings settings);

	/**
	 * Views vanilla settings as carrying the machine book's settings.
	 *
	 * @param settings any recipe book settings
	 * @return the same object
	 */
	static MachineBookSettings of(RecipeBookSettings settings) {
		return (MachineBookSettings) (Object) settings;
	}

	/**
	 * Extends the network codec: the vanilla settings, then the machine book's.
	 *
	 * @param base the vanilla codec
	 * @return the codec that also sends the machine book's settings
	 */
	static StreamCodec<FriendlyByteBuf, RecipeBookSettings> withMachine(
			StreamCodec<FriendlyByteBuf, RecipeBookSettings> base) {
		return StreamCodec.of((buf, settings) -> {
			base.encode(buf, settings);
			TypeSettings.STREAM_CODEC.encode(buf, of(settings).ravecraft$machine());
		}, buf -> {
			RecipeBookSettings settings = base.decode(buf);
			of(settings).ravecraft$setMachine(TypeSettings.STREAM_CODEC.decode(buf));
			return settings;
		});
	}

	/**
	 * Extends the saved codec with two optional keys for the machine book, so older saves load with it closed.
	 *
	 * @param base the vanilla codec
	 * @return the codec that also saves the machine book's settings
	 */
	static MapCodec<RecipeBookSettings> withMachine(MapCodec<RecipeBookSettings> base) {
		MapCodec<TypeSettings> machine = RecordCodecBuilder.mapCodec(i -> i.group(
				Codec.BOOL.optionalFieldOf(OPEN_KEY, false).forGetter(TypeSettings::open),
				Codec.BOOL.optionalFieldOf(FILTERING_KEY, false).forGetter(TypeSettings::filtering)
		).apply(i, TypeSettings::new));
		return RecordCodecBuilder.mapCodec(i -> i.group(
				base.forGetter(settings -> settings),
				machine.forGetter(settings -> of(settings).ravecraft$machine())
		).apply(i, (settings, machineSettings) -> {
			of(settings).ravecraft$setMachine(machineSettings);
			return settings;
		}));
	}
}
