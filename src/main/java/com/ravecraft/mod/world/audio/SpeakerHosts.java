package com.ravecraft.mod.world.audio;

import com.ravecraft.mod.blockentity.PortableSpeakerBlockEntity;
import com.ravecraft.mod.core.speaker.HostTracker;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Server registry of where each speaker id lives, so audio follows the id rather than a block position.
 * Only block hosts for now; carriers and dropped items come later.
 */
public final class SpeakerHosts {
	private static final HostTracker<SpeakerHost> TRACKER = new HostTracker<>();
	/**
	 * Id last placed at each block host: a block entity first registers with its construction id, then gets the
	 * item's id (or a creative refresh), and the stale id must be forgotten, not left pointing at the block.
	 */
	private static final Map<SpeakerHost.Block, UUID> BLOCK_IDS = new HashMap<>();

	private SpeakerHosts() {
	}

	public static void init() {
		ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, level) -> {
			if (blockEntity instanceof PortableSpeakerBlockEntity speaker) {
				placed(speaker);
			}
		});
	}

	/** The speaker {@code id} where it is now; empty when unknown, lost, or its block is not loaded. */
	public static Optional<SpeakerHandle> handle(MinecraftServer server, UUID id) {
		return TRACKER.host(id).flatMap(host -> switch (host) {
			case SpeakerHost.Block block -> blockHandle(server, block, id);
			// Resolved once carried and dropped speakers exist.
			case SpeakerHost.Carrier carrier -> Optional.empty();
			case SpeakerHost.Dropped dropped -> Optional.empty();
		});
	}

	private static Optional<SpeakerHandle> blockHandle(MinecraftServer server, SpeakerHost.Block block, UUID id) {
		ServerLevel level = server.getLevel(block.dimension());
		if (level == null || !level.isLoaded(block.pos())
				|| !(level.getBlockEntity(block.pos()) instanceof PortableSpeakerBlockEntity speaker)
				|| !speaker.data().id().equals(id)) {
			return Optional.empty();
		}
		return Optional.of(new BlockHandle(level, block.pos(), speaker));
	}

	/** Registers a placed or loaded speaker block; a copy of an id already placed elsewhere gets a fresh id. */
	public static void placed(PortableSpeakerBlockEntity speaker) {
		if (!(speaker.getLevel() instanceof ServerLevel level)) {
			return;
		}
		long tick = level.getServer().getTickCount();
		SpeakerHost.Block host = blockHost(level, speaker.getBlockPos());
		UUID previous = BLOCK_IDS.get(host);
		// The previous id no longer exists anywhere: drop it with no grace, so it never expires against this block.
		if (previous != null && !previous.equals(speaker.data().id()) && TRACKER.host(previous).equals(Optional.of(host))) {
			TRACKER.forget(previous);
		}
		if (TRACKER.place(speaker.data().id(), host, tick) == HostTracker.Verdict.DUPLICATE) {
			speaker.setData(speaker.data().withId(UUID.randomUUID()));
			TRACKER.place(speaker.data().id(), host, tick);
		}
		BLOCK_IDS.put(host, speaker.data().id());
	}

	/** The block was broken or its chunk unloaded: the id is lost, and expires unless it shows up again. */
	public static void removed(PortableSpeakerBlockEntity speaker) {
		if (!(speaker.getLevel() instanceof ServerLevel level)) {
			return;
		}
		SpeakerHost.Block host = blockHost(level, speaker.getBlockPos());
		BLOCK_IDS.remove(host, speaker.data().id());
		TRACKER.lose(speaker.data().id(), host, level.getServer().getTickCount());
	}

	/** Called first in each server tick. */
	public static void tick(MinecraftServer server) {
		TRACKER.expire(server.getTickCount());
	}

	/** Whether {@code id} is known, hosted or in its grace after a loss; for tests and diagnostics. */
	public static boolean isTracked(UUID id) {
		return TRACKER.contains(id);
	}

	public static void clear() {
		TRACKER.clear();
		BLOCK_IDS.clear();
	}

	private static SpeakerHost.Block blockHost(ServerLevel level, BlockPos pos) {
		return new SpeakerHost.Block(level.dimension(), pos);
	}
}
