package com.ravecraft.mod.block;

import com.ravecraft.mod.recipe.MachineKind;
import com.ravecraft.mod.registry.ModRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** The coil winder: winds copper into wire and wire into voice coils. */
public class CoilWinderBlock extends MachineBlock {
	private static final double SOUND_CHANCE = 0.1;
	private static final float SOUND_VOLUME = 0.4F;
	private static final double FRONT_OFFSET = 0.52;
	private static final double SPREAD = 0.6;
	private static final double SPARK_HEIGHT = 0.4;

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

	/** While it works: now and then the click of the winding head, and a spark at the front. */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!state.getValue(LIT)) {
			return;
		}
		double x = pos.getX() + 0.5;
		double y = pos.getY();
		double z = pos.getZ() + 0.5;
		if (random.nextDouble() < SOUND_CHANCE) {
			level.playLocalSound(x, y, z, SoundEvents.CRAFTER_CRAFT, SoundSource.BLOCKS, SOUND_VOLUME,
					1.0F + random.nextFloat() * 0.2F, false);
		}
		Direction facing = state.getValue(FACING);
		double across = random.nextDouble() * SPREAD - SPREAD / 2;
		double dx = facing.getAxis() == Direction.Axis.X ? facing.getStepX() * FRONT_OFFSET : across;
		double dz = facing.getAxis() == Direction.Axis.Z ? facing.getStepZ() * FRONT_OFFSET : across;
		double dy = SPARK_HEIGHT + random.nextDouble() * SPARK_HEIGHT;
		level.addParticle(ParticleTypes.ELECTRIC_SPARK, x + dx, y + dy, z + dz, 0, 0, 0);
	}
}
