package com.ravecraft.mod.block;

import com.ravecraft.mod.recipe.MachineKind;
import com.ravecraft.mod.registry.ModRecipes;
import com.ravecraft.mod.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** The magnetizer: magnetizes iron and neodymium into magnets. */
public class MagnetizerBlock extends MachineBlock {
	private static final MachineEffects EFFECTS = new MachineEffects(ModSounds.MAGNETIZER,
			ParticleTypes.ELECTRIC_SPARK);

	/**
	 * A magnetizer.
	 *
	 * @param properties the block properties
	 */
	public MagnetizerBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	public MachineKind kind() {
		return ModRecipes.MAGNETIZING;
	}

	/** While it works: the hum of its transformer, and sparks. */
	@Override
	public MachineEffects effects() {
		return EFFECTS;
	}
}
