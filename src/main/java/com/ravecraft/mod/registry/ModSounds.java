package com.ravecraft.mod.registry;

import com.ravecraft.mod.RaveCraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Sound events: the loops the machines play while they work (clips in {@code sounds/machine}). */
public final class ModSounds {
	/** The coil winder's motor. */
	public static final SoundEvent COIL_WINDER = register("machine.coil_winder");
	/** The magnetizer's transformer hum. */
	public static final SoundEvent MAGNETIZER = register("machine.magnetizer");
	/** The extruder's compressor. */
	public static final SoundEvent EXTRUDER = register("machine.extruder");
	/** The etcher's laser head. */
	public static final SoundEvent ETCHER = register("machine.etcher");

	private ModSounds() {
	}

	private static SoundEvent register(String path) {
		Identifier id = RaveCraft.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	/** Forces class loading, hence registration. */
	public static void init() {
	}
}
