package com.ravecraft.mod.block;

import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.network.chat.Component;

/**
 * Lets common block code open the speaker's music screen without importing client classes. The client installs the
 * opener at startup; on a dedicated server it stays a no-op.
 */
public final class SpeakerScreenOpener {
	private static volatile BiConsumer<UUID, Component> opener = (speakerId, title) -> {
	};

	private SpeakerScreenOpener() {
	}

	public static void set(BiConsumer<UUID, Component> screenOpener) {
		opener = screenOpener;
	}

	/** Opens the screen of speaker {@code speakerId}, titled {@code title}. */
	public static void open(UUID speakerId, Component title) {
		opener.accept(speakerId, title);
	}
}
