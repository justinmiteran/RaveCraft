package com.ravecraft.mod.block;

import com.ravecraft.mod.recipe.MachineKind;
import com.ravecraft.mod.registry.ModRecipes;
import com.ravecraft.mod.registry.ModSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** The etcher: etches circuits and chips with redstone. */
public class EtcherBlock extends MachineBlock {
	private static final MachineEffects EFFECTS = new MachineEffects(ModSounds.ETCHER,
			DustParticleOptions.REDSTONE);

	/**
	 * A etcher.
	 *
	 * @param properties the block properties
	 */
	public EtcherBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	public MachineKind kind() {
		return ModRecipes.ETCHING;
	}

	/** While it works: the whir of its laser head, and redstone dust. */
	@Override
	public MachineEffects effects() {
		return EFFECTS;
	}
}
