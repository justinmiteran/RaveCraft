package com.ravecraft.mod.block;

import com.ravecraft.mod.recipe.MachineKind;
import com.ravecraft.mod.registry.ModRecipes;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** The coil winder: winds copper into wire and wire into voice coils. */
public class CoilWinderBlock extends MachineBlock {
	/**
	 * A coil winder.
	 *
	 * @param properties the block properties
	 */
	public CoilWinderBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	public MachineKind kind() {
		return ModRecipes.WINDING;
	}
}
