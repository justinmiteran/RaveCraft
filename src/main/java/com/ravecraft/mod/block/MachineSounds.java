package com.ravecraft.mod.block;

import com.ravecraft.mod.blockentity.MachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The bridge between working machines and the client sound engine, which common code cannot reach: the client
 * installs a {@link Player}, and each machine's client ticker hands it the machines that work. Nothing plays on a
 * dedicated server, where no player is installed.
 */
public final class MachineSounds {
	private static Player player = (level, pos, machine) -> { };

	private MachineSounds() {
	}

	/** Plays a working machine's loop; called every client tick, so it must keep at most one loop per machine. */
	@FunctionalInterface
	public interface Player {
		/**
		 * A machine works this tick.
		 *
		 * @param level the client level
		 * @param pos the machine's position
		 * @param machine the machine's block
		 */
		void working(Level level, BlockPos pos, MachineBlock machine);
	}

	/**
	 * Installs the client's player.
	 *
	 * @param installed the player
	 */
	public static void install(Player installed) {
		player = installed;
	}

	/**
	 * The machines' client ticker: hands the machine to the player while it is lit.
	 *
	 * @param level the client level
	 * @param pos the machine's position
	 * @param state the machine's state
	 * @param entity the machine's block entity, unused
	 */
	public static void clientTick(Level level, BlockPos pos, BlockState state, MachineBlockEntity entity) {
		if (state.getValue(MachineBlock.LIT) && state.getBlock() instanceof MachineBlock machine) {
			player.working(level, pos, machine);
		}
	}
}
