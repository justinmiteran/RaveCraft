package com.ravecraft.mod.block;

import com.ravecraft.mod.recipe.MachineKind;
import com.ravecraft.mod.registry.ModRecipes;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** The etcher: etches circuits and chips with redstone. */
public class EtcherBlock extends MachineBlock {
	private static final MachineEffects EFFECTS = new MachineEffects(SoundEvents.SCULK_CLICKING, 1.0F,
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

	/** While it works: the clicking of its head and redstone dust. */
	@Override
	protected MachineEffects effects() {
		return EFFECTS;
	}
}
