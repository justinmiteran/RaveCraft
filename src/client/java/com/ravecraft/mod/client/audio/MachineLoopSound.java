package com.ravecraft.mod.client.audio;

import com.ravecraft.mod.block.MachineBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A working machine's loop, played at the machine for as long as it stays the same lit machine in a loaded chunk of
 * the player's level. It stops itself otherwise, so a broken, idle or unloaded machine falls silent.
 */
final class MachineLoopSound extends AbstractTickableSoundInstance {
	private static final float VOLUME = 0.5F;
	private final Level level;
	private final BlockPos pos;
	private final MachineBlock machine;
	private final Runnable onStop;

	/**
	 * The loop of a machine.
	 *
	 * @param level the client level the machine is in
	 * @param pos the machine's position
	 * @param machine the machine's block, whose effects give the loop
	 * @param onStop run once when the loop stops
	 */
	MachineLoopSound(Level level, BlockPos pos, MachineBlock machine, Runnable onStop) {
		super(machine.effects().sound(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
		this.level = level;
		this.pos = pos;
		this.machine = machine;
		this.onStop = onStop;
		this.looping = true;
		this.delay = 0;
		this.volume = VOLUME;
		this.x = pos.getX() + 0.5;
		this.y = pos.getY() + 0.5;
		this.z = pos.getZ() + 0.5;
	}

	/** Starts even out of earshot, so it is heard on the way in. */
	@Override
	public boolean canStartSilent() {
		return true;
	}

	@Override
	public void tick() {
		if (!stillWorking()) {
			stop();
			onStop.run();
		}
	}

	private boolean stillWorking() {
		if (Minecraft.getInstance().level != level || !level.isLoaded(pos)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		return state.is(machine) && state.getValue(MachineBlock.LIT);
	}
}
