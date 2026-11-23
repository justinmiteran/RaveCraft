package com.ravecraft.mod.block;

import com.ravecraft.mod.recipe.MachineKind;
import com.ravecraft.mod.registry.ModRecipes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** The extruder: shapes bitumen in a mold into plastic parts. */
public class ExtruderBlock extends MachineBlock {
	private static final MachineEffects EFFECTS = new MachineEffects(SoundEvents.LAVA_POP, 1.0F,
			ParticleTypes.SMOKE);

	/**
	 * A extruder.
	 *
	 * @param properties the block properties
	 */
	public ExtruderBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	public MachineKind kind() {
		return ModRecipes.EXTRUDING;
	}

	/** While it works: the pop of hot bitumen and smoke. */
	@Override
	protected MachineEffects effects() {
		return EFFECTS;
	}
}
