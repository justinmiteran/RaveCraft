package com.ravecraft.mod.block;

import com.ravecraft.mod.recipe.MachineKind;
import com.ravecraft.mod.registry.ModRecipes;
import com.ravecraft.mod.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** The extruder: shapes bitumen in a mold into plastic parts. */
public class ExtruderBlock extends MachineBlock {
	private static final MachineEffects EFFECTS = new MachineEffects(ModSounds.EXTRUDER,
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

	/** While it works: the rattle of its compressor, and smoke. */
	@Override
	public MachineEffects effects() {
		return EFFECTS;
	}
}
