package com.ravecraft.mod.world.audio;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Where a speaker id currently lives: a placed block, a player carrying the item, or the item on the ground. */
public sealed interface SpeakerHost {
	record Block(ResourceKey<Level> dimension, BlockPos pos) implements SpeakerHost {
		public Block {
			pos = pos.immutable();
		}
	}

	/** {@code inHand}: held in a hand (sounds from the eyes) rather than elsewhere in the inventory (chest). */
	record Carrier(UUID player, ResourceKey<Level> dimension, boolean inHand) implements SpeakerHost {}

	record Dropped(UUID entity, ResourceKey<Level> dimension) implements SpeakerHost {}
}
