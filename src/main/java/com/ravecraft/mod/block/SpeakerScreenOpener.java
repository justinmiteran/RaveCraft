package com.ravecraft.mod.block;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;

/**
 * Lets common block code open the speaker's music screen without importing client classes. The client installs the
 * opener at startup; on a dedicated server it stays a no-op.
 */
public final class SpeakerScreenOpener {
	private static volatile Consumer<BlockPos> opener = pos -> {
	};

	private SpeakerScreenOpener() {
	}

	/**
	 * Sets what opens the speaker screen; the client calls it at startup.
	 *
	 * @param screenOpener opens the screen of the speaker at a position
	 */
	public static void set(Consumer<BlockPos> screenOpener) {
		opener = screenOpener;
	}

	/**
	 * Opens the screen of the speaker at {@code pos}, when a client set an opener.
	 *
	 * @param pos where the speaker stands
	 */
	public static void open(BlockPos pos) {
		opener.accept(pos);
	}
}
