package com.ravecraft.mod.block;

import com.ravecraft.mod.core.speaker.SpeakerKind;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A small battery-powered speaker that keeps its identity and settings when picked up. */
public class PortableSpeakerBlock extends SpeakerBlock {
	// Barrel body (x 1..15, y 0..9, z 3.5..12.5) plus the carry handle (x 4..12, up to y 13.5), from
	// models/block/portable_speaker.json; the long axis follows X when facing north or south.
	private static final VoxelShape SHAPE_NORTH_SOUTH = Shapes.or(Block.box(1, 0, 3.5, 15, 9, 12.5), Block.box(4, 9, 7, 12, 12.5, 9));
	private static final VoxelShape SHAPE_EAST_WEST = Shapes.or(Block.box(3.5, 0, 1, 12.5, 9, 15), Block.box(7, 9, 4, 9, 12.5, 12));

	/** A portable speaker, unpowered and facing north by default. */
	public PortableSpeakerBlock(BlockBehaviour.Properties properties) {
		super(properties, SpeakerKind.PORTABLE, SHAPE_NORTH_SOUTH, SHAPE_EAST_WEST);
	}
}
