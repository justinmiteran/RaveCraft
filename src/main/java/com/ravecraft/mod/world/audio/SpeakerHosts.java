package com.ravecraft.mod.world.audio;

import com.ravecraft.mod.blockentity.PortableSpeakerBlockEntity;
import com.ravecraft.mod.core.audio.stream.SessionNotices;
import com.ravecraft.mod.core.audio.stream.SessionTable.Session;
import com.ravecraft.mod.core.speaker.HostTracker;
import com.ravecraft.mod.network.Net;
import com.ravecraft.mod.network.payload.SpeakerHostPayload;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

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
	/**
	 * Where each open session's speaker is, its volume and power, told once to each player who hears it: rebuilt
	 * and sent again to everyone hearing it on each change, and to a new listener before its first relayed chunk.
	 */
	private static final SessionNotices<SpeakerHostPayload> NOTICES = new SessionNotices<>();

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

	/**
	 * The handle of a speaker block entity built directly, without the registry: still valid while the block entity
	 * is being removed, when {@link #handle} no longer resolves it. Empty off the server.
	 */
	public static Optional<SpeakerHandle> handleOf(PortableSpeakerBlockEntity speaker) {
		return speaker.getLevel() instanceof ServerLevel level
				? Optional.of(new BlockHandle(level, speaker.getBlockPos(), speaker))
				: Optional.empty();
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
			// Clients open the screen by the id they hold: send them the new one.
			level.sendBlockUpdated(speaker.getBlockPos(), speaker.getBlockState(), speaker.getBlockState(), Block.UPDATE_CLIENTS);
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
		NOTICES.clear();
	}

	// --- Host notices ------------------------------------------------------------------------------------------

	/**
	 * (Re)builds the host notice of speaker {@code speakerId} while it plays, and sends it to the players tracking it
	 * and to the session owner. Called when the session starts and when the host, volume or power change; a no-op
	 * when it does not play or its host is not reachable.
	 */
	public static void notice(MinecraftServer server, UUID speakerId) {
		Optional<Session<UUID>> session = StreamSessions.table().byKey(speakerId);
		Optional<SpeakerHandle> handle = handle(server, speakerId);
		if (session.isEmpty() || handle.isEmpty()) {
			return;
		}
		int sessionId = session.get().id();
		NOTICES.open(sessionId, payloadOf(handle.get()));
		Set<ServerPlayer> hearers = new LinkedHashSet<>(handle.get().trackers());
		ServerPlayer owner = server.getPlayerList().getPlayer(session.get().owner());
		if (owner != null) {
			hearers.add(owner);
		}
		for (ServerPlayer player : hearers) {
			tellHost(player, sessionId);
		}
	}

	/** Sends the host of session {@code sessionId} to {@code player} unless already told; returns whether it sent. */
	public static boolean tellHost(ServerPlayer player, int sessionId) {
		Optional<SpeakerHostPayload> notice = NOTICES.firstFor(sessionId, player.getUUID());
		notice.ifPresent(payload -> Net.send(player, payload));
		return notice.isPresent();
	}

	public static Optional<SpeakerHostPayload> noticeOf(int sessionId) {
		return NOTICES.get(sessionId);
	}

	/** Session {@code sessionId} ended: drops its notice and returns everyone told about it, who may be hearing it. */
	static Set<UUID> endNotice(int sessionId) {
		Set<UUID> reached = NOTICES.reached(sessionId);
		NOTICES.end(sessionId);
		return reached;
	}

	/** {@code player} left: tell them again if they come back while the same sessions play. */
	public static void forget(UUID player) {
		NOTICES.forget(player);
	}

	/** Drops every notice without telling anyone: the sessions are all gone. */
	static void clearNotices() {
		NOTICES.clear();
	}

	private static SpeakerHostPayload payloadOf(SpeakerHandle handle) {
		int volume = handle.data().settings().volume();
		boolean powered = handle.powered();
		return switch (handle.host()) {
			case SpeakerHost.Block block -> new SpeakerHostPayload(handle.id(), block.dimension(), Optional.of(block.pos()),
					SpeakerHostPayload.NO_ENTITY, false, volume, powered);
			case SpeakerHost.Carrier carrier -> new SpeakerHostPayload(handle.id(), carrier.dimension(), Optional.empty(),
					entityId(handle.level().getServer().getPlayerList().getPlayer(carrier.player())), carrier.inHand(),
					volume, powered);
			case SpeakerHost.Dropped dropped -> new SpeakerHostPayload(handle.id(), dropped.dimension(), Optional.empty(),
					entityId(handle.level().getEntity(dropped.entity())), false, volume, powered);
		};
	}

	private static int entityId(@Nullable Entity entity) {
		return entity != null ? entity.getId() : SpeakerHostPayload.NO_ENTITY;
	}

	private static SpeakerHost.Block blockHost(ServerLevel level, BlockPos pos) {
		return new SpeakerHost.Block(level.dimension(), pos);
	}
}
