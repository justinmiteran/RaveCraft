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

	public static void set(Consumer<BlockPos> screenOpener) {
		opener = screenOpener;
	}

	public static void open(BlockPos pos) {
		opener.accept(pos);
	}
}
