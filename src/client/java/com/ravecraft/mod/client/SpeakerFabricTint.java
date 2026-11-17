package com.ravecraft.mod.client;

import com.ravecraft.mod.block.PortableSpeakerBlock;
import com.ravecraft.mod.blockentity.PortableSpeakerBlockEntity;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;

/** Tint layer 0 of the portable speaker: its fabric, coloured by the dye stored in the block entity. */
public final class SpeakerFabricTint implements BlockTintSource {
	@Override
	public int color(BlockState state) {
		return ARGB.opaque(PortableSpeakerBlock.DEFAULT_COLOR);
	}

	@Override
	public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof PortableSpeakerBlockEntity speaker
				? ARGB.opaque(speaker.fabricColor())
				: color(state);
	}
}
