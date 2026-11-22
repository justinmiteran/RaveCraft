package com.ravecraft.mod.client;

import com.ravecraft.mod.block.SpeakerBlock;
import com.ravecraft.mod.blockentity.SpeakerBlockEntity;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;

/** Tint layer 0 of a speaker: the portable's fabric or the party speaker's grille, coloured by the stored dye. */
public final class SpeakerFabricTint implements BlockTintSource {
	@Override
	public int color(BlockState state) {
		return ARGB.opaque(SpeakerBlock.DEFAULT_COLOR);
	}

	@Override
	public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof SpeakerBlockEntity speaker
				? ARGB.opaque(speaker.fabricColor())
				: color(state);
	}
}
