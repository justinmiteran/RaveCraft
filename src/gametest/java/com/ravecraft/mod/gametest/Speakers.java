package com.ravecraft.mod.gametest;

import com.ravecraft.mod.blockentity.SpeakerBlockEntity;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

/** Shared helpers for the speaker GameTests. */
final class Speakers {
	private Speakers() {
	}

	/** The id of the speaker block entity at {@code absPos} (absolute), as the server tables key it. */
	static UUID idAt(GameTestHelper h, BlockPos absPos) {
		if (!(h.getLevel().getBlockEntity(absPos) instanceof SpeakerBlockEntity speaker)) {
			throw h.assertionException(Component.literal("No portable speaker block entity at " + absPos.toShortString()));
		}
		return speaker.data().id();
	}
}
