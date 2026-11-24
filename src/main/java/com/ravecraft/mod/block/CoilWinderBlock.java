package com.ravecraft.mod.block;

import com.ravecraft.mod.recipe.MachineKind;
import com.ravecraft.mod.registry.ModRecipes;
import com.ravecraft.mod.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** The coil winder: winds copper into wire and wire into voice coils. */
public class CoilWinderBlock extends MachineBlock {
	private static final MachineEffects EFFECTS = new MachineEffects(ModSounds.COIL_WINDER,
			ParticleTypes.ELECTRIC_SPARK);

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

	/** While it works: the whine of the winding motor, and sparks. */
	@Override
	public MachineEffects effects() {
		return EFFECTS;
	}
}
