package com.ravecraft.mod.world.audio;

import com.ravecraft.mod.blockentity.SpeakerBlockEntity;
import com.ravecraft.mod.component.SpeakerData;
import com.ravecraft.mod.core.queue.SpeakerQueue;
import com.ravecraft.mod.core.speaker.CarryPlace;
import com.ravecraft.mod.core.speaker.HostTracker;
import com.ravecraft.mod.registry.ModDataComponents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * Server registry of where each speaker id lives, so audio follows the id rather than a block position: a placed
 * block, a player carrying the item (reported every tick from {@code inventoryTick}) or the item on the ground. When a
 * host is lost for good, {@link #tick} decides what happens to its session.
 */
public final class SpeakerHosts {
	private static final HostTracker<SpeakerHost> TRACKER = new HostTracker<>();
	/**
	 * Id last placed at each block host: a block entity first registers with its construction id, then gets the
	 * item's id (or a creative refresh), and the stale id must be forgotten, not left pointing at the block.
	 */
	private static final Map<SpeakerHost.Block, UUID> BLOCK_IDS = new HashMap<>();
	/** Last queue seen for each tracked id: what a session is paused at once its host is out of reach (a chest). */
	private static final Map<UUID, SpeakerQueue> LAST_QUEUE = new HashMap<>();
	/** Sessions paused because their host was lost, waiting for their id to show up again. Memory only. */
	private static final Map<UUID, Parked> PARKED = new HashMap<>();
	/** Speaker id of each dropped item entity: a pickup empties the stack before the entity unloads. */
	private static final Map<UUID, UUID> DROPPED_IDS = new HashMap<>();
	/** How each lost dropped host was lost, read when it expires. */
	private static final Map<UUID, DropLoss> DROP_LOSSES = new HashMap<>();
	/** Tick of the last accepted carrier report of each id: tells a player moving its own speaker from a copy. */
	private static final Map<UUID, Long> CARRIED_AT = new HashMap<>();
	/**
	 * Drops that met a live placed host, tried again at the end of the tick: a broken block drops its item before
	 * its block entity is removed. Still meeting one then, the drop is a copy and gets a fresh id.
	 */
	private static final List<ItemEntity> PENDING_DROPS = new ArrayList<>();

	/** {@code carrier}: the player it was lost from, whose disconnect cancels {@code resume}; null otherwise. */
	private record Parked(SpeakerQueue queue, boolean resume, @Nullable UUID carrier) {}

	/** How a dropped host was lost. */
	private enum DropLoss {
		/** Its stack was emptied: taken by a player or a hopper, where it plays again once carried. */
		PICKED_UP,
		/** Its chunk unloaded (or it changed dimension and was not seen again). */
		UNLOADED,
		/** Despawned or fell out of the world with its stack. */
		GONE
	}

	private SpeakerHosts() {
	}

	/** Follows speaker blocks and item entities as they load and unload. */
	public static void init() {
		ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, level) -> {
			if (blockEntity instanceof SpeakerBlockEntity speaker) {
				placed(speaker);
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof ItemEntity item) {
				dropped(item);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof ItemEntity item) {
				droppedRemoved(item);
			}
		});
	}

	/** The speaker {@code id} where it is now; empty when unknown, lost, or its host is not loaded. */
	public static Optional<SpeakerHandle> handle(MinecraftServer server, UUID id) {
		return TRACKER.host(id).flatMap(host -> SpeakerLookup.handle(server, host, id));
	}

	/** Registers a placed or loaded speaker block; a copy of an id already placed elsewhere gets a fresh id. */
	public static void placed(SpeakerBlockEntity speaker) {
		if (!(speaker.getLevel() instanceof ServerLevel level)) {
			return;
		}
		long tick = level.getServer().getTickCount();
		SpeakerHost.Block host = blockHost(level, speaker.getBlockPos());
		UUID previous = BLOCK_IDS.get(host);
		// The previous id no longer exists anywhere: drop it with no grace, so it never expires against this block.
		if (previous != null && !previous.equals(speaker.data().id()) && TRACKER.host(previous).equals(Optional.of(host))) {
			forgetId(previous);
		}
		HostTracker.Verdict verdict = TRACKER.place(speaker.data().id(), host, tick);
		if (verdict == HostTracker.Verdict.DUPLICATE) {
			speaker.setData(speaker.data().withId(UUID.randomUUID()));
			verdict = TRACKER.place(speaker.data().id(), host, tick);
			// Clients open the screen by the id they hold: send them the new one.
			level.sendBlockUpdated(speaker.getBlockPos(), speaker.getBlockState(), speaker.getBlockState(), Block.UPDATE_CLIENTS);
		}
		BLOCK_IDS.put(host, speaker.data().id());
		arrived(level.getServer(), new BlockHandle(level, speaker.getBlockPos(), speaker), verdict);
	}

	/** The block was broken or its chunk unloaded: the id is lost, and expires unless it shows up again. */
	public static void removed(SpeakerBlockEntity speaker) {
		if (!(speaker.getLevel() instanceof ServerLevel level)) {
			return;
		}
		SpeakerHost.Block host = blockHost(level, speaker.getBlockPos());
		BLOCK_IDS.remove(host, speaker.data().id());
		TRACKER.lose(speaker.data().id(), host, level.getServer().getTickCount());
	}

	/**
	 * {@code player} carries {@code stack} this tick, at {@code place}: in a hand, in its inventory or on its back.
	 * Called every tick for each carried speaker; a fresh stack gets its identity here, a copy a new one.
	 */
	public static void carried(ServerPlayer player, ItemStack stack, CarryPlace place) {
		ServerLevel level = player.level();
		long tick = level.getServer().getTickCount();
		SpeakerData data = stack.get(ModDataComponents.SPEAKER);
		if (data == null) {
			data = SpeakerData.create();
			stack.set(ModDataComponents.SPEAKER, data);
		}
		SpeakerHost.Carrier host = new SpeakerHost.Carrier(player.getUUID(), level.dimension(), place);
		UUID id = data.id();
		// The same player moving it between hand, pocket and back, or through a portal, is a move: unless the id already
		// reported this tick, which means two of its stacks share the id.
		if (TRACKER.host(id).orElse(null) instanceof SpeakerHost.Carrier current && current.player().equals(host.player())
				&& !current.equals(host) && !Long.valueOf(tick).equals(CARRIED_AT.get(id))) {
			TRACKER.lose(id, current, tick);
		}
		HostTracker.Verdict verdict = TRACKER.report(id, host, tick);
		if (verdict == HostTracker.Verdict.DUPLICATE) {
			data = data.withId(UUID.randomUUID());
			stack.set(ModDataComponents.SPEAKER, data);
			id = data.id();
			verdict = TRACKER.report(id, host, tick);
		}
		CARRIED_AT.put(id, tick);
		arrived(level.getServer(), new CarrierHandle(player, stack, place), verdict);
	}

	/** A speaker item entity was added to or loaded in a level. Items without a speaker identity are ignored. */
	public static void dropped(ItemEntity item) {
		if (item.level() instanceof ServerLevel level && placeDropped(level, item, false)) {
			PENDING_DROPS.add(item);
		}
	}

	/** Places {@code item} as a dropped host; returns true when it met a live placed host and should be retried. */
	private static boolean placeDropped(ServerLevel level, ItemEntity item, boolean retry) {
		SpeakerData data = item.getItem().get(ModDataComponents.SPEAKER);
		if (data == null || item.isRemoved()) {
			return false;
		}
		long tick = level.getServer().getTickCount();
		SpeakerHost.Dropped host = new SpeakerHost.Dropped(item.getUUID(), level.dimension());
		HostTracker.Verdict verdict = TRACKER.place(data.id(), host, tick);
		if (verdict == HostTracker.Verdict.DUPLICATE) {
			if (!retry) {
				return true;
			}
			data = data.withId(UUID.randomUUID());
			item.getItem().set(ModDataComponents.SPEAKER, data);
			verdict = TRACKER.place(data.id(), host, tick);
		}
		DROPPED_IDS.put(item.getUUID(), data.id());
		arrived(level.getServer(), new DroppedHandle(level, item), verdict);
		return false;
	}

	/** A speaker item entity left its level: picked up, unloaded with its chunk, despawned or destroyed. */
	public static void droppedRemoved(ItemEntity item) {
		PENDING_DROPS.remove(item);
		UUID id = DROPPED_IDS.remove(item.getUUID());
		if (id == null || !(item.level() instanceof ServerLevel level)) {
			return;
		}
		SpeakerHost.Dropped host = new SpeakerHost.Dropped(item.getUUID(), level.dimension());
		if (!TRACKER.host(id).equals(Optional.of(host))) {
			return;
		}
		// A chunk hides its entities before it sets their removal reason: no reason yet also means unloaded.
		Entity.RemovalReason reason = item.getRemovalReason();
		DropLoss loss = item.getItem().isEmpty() ? DropLoss.PICKED_UP
				: reason == null || !reason.shouldDestroy() ? DropLoss.UNLOADED
				: DropLoss.GONE;
		DROP_LOSSES.put(id, loss);
		TRACKER.lose(id, host, level.getServer().getTickCount());
	}

	/** The speaker item burnt, exploded or was otherwise destroyed: its session ends for good. */
	public static void destroyed(MinecraftServer server, UUID id) {
		SpeakerPlayback.stopAt(server, id);
		forgetId(id);
		PARKED.remove(id);
		// Open screens close: the next flush finds the speaker gone.
		SpeakerWatchers.markDirty(id);
	}

	/** {@code player} is leaving: the speakers it carries pause, their position written into its inventory. */
	public static void disconnected(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		// Only on a disconnect: walks the tracked ids rather than keeping an index per player.
		for (UUID id : List.copyOf(LAST_QUEUE.keySet())) {
			if (TRACKER.host(id).orElse(null) instanceof SpeakerHost.Carrier carrier && carrier.player().equals(player.getUUID())) {
				SpeakerQueue last = LAST_QUEUE.get(id);
				// Searches the leaving player itself, which may already be out of the player list.
				SpeakerLookup.findCarried(player, id).ifPresentOrElse(
						stack -> SpeakerPlayback.pauseRemoved(new CarrierHandle(player, stack, carrier.place())),
						() -> SpeakerPlayback.detach(server, id, last));
				forgetId(id);
				SpeakerWatchers.markDirty(id);
			}
		}
		// Paused speakers lost from this player stay paused when it comes back: a disconnect pauses.
		PARKED.replaceAll((id, parked) -> parked.resume() && player.getUUID().equals(parked.carrier())
				? new Parked(parked.queue(), false, parked.carrier())
				: parked);
	}

	/**
	 * A host of {@code handle}'s id reported or was placed. A session parked when it was lost gets its queue back
	 * (and plays again if asked); a playing one that moved tells its listeners where it is now.
	 */
	private static void arrived(MinecraftServer server, SpeakerHandle handle, HostTracker.Verdict verdict) {
		UUID id = handle.id();
		LAST_QUEUE.put(id, handle.data().queue());
		Parked parked = PARKED.remove(id);
		if (parked != null) {
			handle.setData(handle.data().withQueue(parked.queue()));
			if (parked.resume()) {
				SpeakerPlayback.resume(handle);
			}
		} else if (verdict == HostTracker.Verdict.MOVED && SpeakerPlayback.isPlaying(id)) {
			SpeakerHostNotices.notice(server, id);
		}
	}

	/** The queue of {@code id} was written where it lives; kept for when that host is no longer reachable. */
	static void stored(UUID id, SpeakerQueue queue) {
		if (TRACKER.contains(id)) {
			LAST_QUEUE.put(id, queue);
		}
	}

	/**
	 * Called once per server tick: retries drops that met a live host, then settles the ids whose host was lost
	 * for {@link HostTracker#LOST_AFTER_TICKS}. A carried speaker that went into a chest, or a drop taken by a
	 * hopper, pauses and plays again once carried; a block or a drop unloaded with its chunk pauses; a drop that
	 * despawned stops.
	 */
	public static void tick(MinecraftServer server) {
		retryDrops();
		for (HostTracker.Expired<SpeakerHost> expired : TRACKER.expire(server.getTickCount())) {
			if (settle(server, expired)) {
				// Open screens close (or refresh): the next flush finds the speaker gone.
				SpeakerWatchers.markDirty(expired.id());
			}
		}
	}

	/** Places again the drops that met a live placed host this tick; still meeting one, a drop gets a fresh id. */
	private static void retryDrops() {
		if (PENDING_DROPS.isEmpty()) {
			return;
		}
		List<ItemEntity> pending = List.copyOf(PENDING_DROPS);
		PENDING_DROPS.clear();
		for (ItemEntity item : pending) {
			if (item.level() instanceof ServerLevel level) {
				placeDropped(level, item, true);
			}
		}
	}

	/**
	 * Settles an id whose host was lost for good, by its last host; returns false when a carrier still holds it
	 * where nothing ticks it, which keeps it playing.
	 */
	private static boolean settle(MinecraftServer server, HostTracker.Expired<SpeakerHost> expired) {
		UUID id = expired.id();
		SpeakerQueue last = LAST_QUEUE.remove(id);
		DropLoss loss = DROP_LOSSES.remove(id);
		CARRIED_AT.remove(id);
		switch (expired.lastHost()) {
			case SpeakerHost.Carrier carrier -> {
				if (reportUnticked(server, carrier, id)) {
					return false;
				}
				park(server, id, last, true, carrier.player());
			}
			case SpeakerHost.Dropped _ -> settleDrop(server, id, last, loss);
			case SpeakerHost.Block _ -> park(server, id, last, false, null);
		}
		return true;
	}

	/** A lost drop: gone with its stack, it stops; otherwise it pauses, and plays again once carried if picked up. */
	private static void settleDrop(MinecraftServer server, UUID id, @Nullable SpeakerQueue last, @Nullable DropLoss loss) {
		if (loss == DropLoss.GONE) {
			SpeakerPlayback.stopAt(server, id);
		} else {
			park(server, id, last, loss == DropLoss.PICKED_UP, null);
		}
	}

	/** Ends the session of lost speaker {@code id}; a playing one keeps its live queue until the id shows up again. */
	private static void park(MinecraftServer server, UUID id, @Nullable SpeakerQueue last, boolean resume, @Nullable UUID carrier) {
		boolean playing = SpeakerPlayback.isPlaying(id);
		SpeakerQueue live = SpeakerPlayback.detach(server, id, last != null ? last : SpeakerQueue.EMPTY);
		if (playing && last != null) {
			PARKED.put(id, new Parked(live, resume, carrier));
		}
	}

	/**
	 * A carrier stopped reporting {@code id}: if it still holds it where nothing ticks it (cursor, crafting grid),
	 * report it from there and keep playing. Looked up once per grace, not every tick.
	 */
	private static boolean reportUnticked(MinecraftServer server, SpeakerHost.Carrier carrier, UUID id) {
		ServerPlayer player = server.getPlayerList().getPlayer(carrier.player());
		Optional<ItemStack> stack = player == null ? Optional.empty() : SpeakerLookup.findUnticked(player, id);
		stack.ifPresent(found -> carried(player, found, CarryPlace.INVENTORY));
		return stack.isPresent();
	}

	private static void forgetId(UUID id) {
		TRACKER.forget(id);
		LAST_QUEUE.remove(id);
		DROP_LOSSES.remove(id);
		CARRIED_AT.remove(id);
	}

	/** Whether {@code id} is known, hosted or in its grace after a loss; for tests and diagnostics. */
	public static boolean isTracked(UUID id) {
		return TRACKER.contains(id);
	}

	/** The queue a lost playing speaker was paused at, until its id shows up again; for tests and diagnostics. */
	public static Optional<SpeakerQueue> parkedQueue(UUID id) {
		return Optional.ofNullable(PARKED.get(id)).map(Parked::queue);
	}

	/** Forgets every host, notice and parked session: the server is stopping. */
	public static void clear() {
		TRACKER.clear();
		BLOCK_IDS.clear();
		SpeakerHostNotices.clear();
		LAST_QUEUE.clear();
		PARKED.clear();
		DROPPED_IDS.clear();
		DROP_LOSSES.clear();
		CARRIED_AT.clear();
		PENDING_DROPS.clear();
	}

	private static SpeakerHost.Block blockHost(ServerLevel level, BlockPos pos) {
		return new SpeakerHost.Block(level.dimension(), pos);
	}
}
