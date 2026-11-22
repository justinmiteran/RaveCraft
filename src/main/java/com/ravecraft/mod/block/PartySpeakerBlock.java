package com.ravecraft.mod.block;

import com.ravecraft.mod.core.speaker.SpeakerKind;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.VoxelShape;

/** An upright, directional speaker that throws its sound forward: louder and farther than the portable. */
public class PartySpeakerBlock extends SpeakerBlock {
	// Outer box of models/block/party_speaker.json with its rubber corners (x 1..15, z 2..14, y 0..20), clamped
	// to 16 px high so the block never fills two blocks; the long side follows X when facing north or south.
	private static final VoxelShape SHAPE_NORTH_SOUTH = Block.box(1, 0, 2, 15, 16, 14);
	private static final VoxelShape SHAPE_EAST_WEST = Block.box(2, 0, 1, 14, 16, 15);

	/**
	 * A party speaker, unpowered and facing north by default.
	 *
	 * @param properties the block's properties
	 */
	public PartySpeakerBlock(BlockBehaviour.Properties properties) {
		super(properties, SpeakerKind.PARTY, SHAPE_NORTH_SOUTH, SHAPE_EAST_WEST);
	}
}
