package com.ravecraft.mod.block;

import com.ravecraft.mod.recipe.MachineKind;
import com.ravecraft.mod.registry.ModRecipes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** The magnetizer: magnetizes iron and neodymium into magnets. */
public class MagnetizerBlock extends MachineBlock {
	private static final MachineEffects EFFECTS = new MachineEffects(SoundEvents.BEACON_AMBIENT, 1.0F,
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

	/** While it works: the hum of its field and sparks. */
	@Override
	protected MachineEffects effects() {
		return EFFECTS;
	}
}
