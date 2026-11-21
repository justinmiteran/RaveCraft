package com.ravecraft.mod.registry;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.component.SpeakerData;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

/** Data components: the speaker's data carried by its item. */
public final class ModDataComponents {
	public static final DataComponentType<SpeakerData> SPEAKER = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			RaveCraft.id("speaker"),
			DataComponentType.<SpeakerData>builder()
					.persistent(SpeakerData.CODEC)
					.networkSynchronized(SpeakerData.STREAM_CODEC)
					.build());

	private ModDataComponents() {
	}

	/** Forces class loading, hence registration. */
	public static void init() {
	}
}
