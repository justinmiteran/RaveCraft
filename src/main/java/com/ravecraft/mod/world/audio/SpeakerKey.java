package com.ravecraft.mod.world.audio;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Where a placed speaker is: its dimension and block position. */
public record SpeakerKey(ResourceKey<Level> dimension, BlockPos pos) {
	/** Keeps an immutable copy of {@code pos}. */
	public SpeakerKey {
		pos = pos.immutable();
	}
}
