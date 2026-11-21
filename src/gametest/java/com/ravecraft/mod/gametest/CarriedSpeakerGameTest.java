package com.ravecraft.mod.gametest;

import static com.ravecraft.mod.gametest.SharedQueueGameTest.leave;
import static com.ravecraft.mod.gametest.SharedQueueGameTest.newEntries;
import static com.ravecraft.mod.gametest.SharedQueueGameTest.online;
import static com.ravecraft.mod.gametest.SharedQueueGameTest.poweredSpeaker;

import com.ravecraft.mod.blockentity.SpeakerBlockEntity;
import com.ravecraft.mod.component.SpeakerData;
import com.ravecraft.mod.core.queue.QueueEntry;
import com.ravecraft.mod.core.queue.SpeakerQueue;
import com.ravecraft.mod.core.speaker.CarryPlace;
import com.ravecraft.mod.core.speaker.SpeakerSettings;
import com.ravecraft.mod.network.payload.SpeakerCommandPayload.Command;
import com.ravecraft.mod.registry.ModBlocks;
import com.ravecraft.mod.registry.ModDataComponents;
import com.ravecraft.mod.world.audio.SpeakerHost;
import com.ravecraft.mod.world.audio.SpeakerCommands;
import com.ravecraft.mod.world.audio.SpeakerHosts;
import com.ravecraft.mod.world.audio.SpeakerPlayback;
import com.ravecraft.mod.world.audio.SpeakerWatchers;
import com.ravecraft.mod.world.audio.StreamSessions;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.Vec3;

/** A speaker keeps its session while carried or lying on the ground, and the session follows its id across hosts. */
public class CarriedSpeakerGameTest {
	private static final BlockPos SPEAKER = new BlockPos(1, 1, 1);
	private static final long DURATION_MS = 180_000;
	/** A main inventory slot outside the hotbar. */
	private static final int INVENTORY_SLOT = 20;

	private static MinecraftServer server(GameTestHelper h) {
		return h.getLevel().getServer();
	}

	/** A powered speaker item with a fresh id. */
	private static ItemStack speakerStack() {
		ItemStack stack = new ItemStack(ModBlocks.PORTABLE_SPEAKER);
		stack.set(ModDataComponents.SPEAKER, new SpeakerData(UUID.randomUUID(), new SpeakerSettings(true, SpeakerSettings.DEFAULT.volume())));
		return stack;
	}

	private static UUID idOf(ItemStack stack) {
		return stack.get(ModDataComponents.SPEAKER).id();
	}

	/** Queues one track of {@code owner} on speaker {@code id}, plays it and opens its stream. */
	static void play(GameTestHelper h, ServerPlayer owner, UUID id) {
		SpeakerCommands.add(owner, id, newEntries(1));
		SpeakerCommands.command(owner, id, Command.PLAY, Optional.empty());
		int session = StreamSessions.table().byKey(id).orElseThrow().id();
		SpeakerPlayback.onStreamStarted(owner, session, DURATION_MS);
		h.assertTrue(SpeakerPlayback.isPlaying(id), "playing at the start");
	}

	/** A playing speaker held in the main hand of {@code owner}. */
	private static ItemStack carriedPlaying(GameTestHelper h, ServerPlayer owner) {
		ItemStack stack = speakerStack();
		owner.getInventory().setItem(owner.getInventory().getSelectedSlot(), stack);
		SpeakerHosts.carried(owner, stack, CarryPlace.HAND);
		play(h, owner, idOf(stack));
		return stack;
	}

	/** A playing speaker lying still on the ground at {@link #SPEAKER}. */
	private static ItemEntity droppedPlaying(GameTestHelper h, ServerPlayer owner) {
		Vec3 centre = Vec3.atCenterOf(h.absolutePos(SPEAKER));
		ItemEntity entity = new ItemEntity(h.getLevel(), centre.x, centre.y, centre.z, speakerStack());
		entity.setNoGravity(true);
		entity.setDeltaMovement(Vec3.ZERO);
		h.getLevel().addFreshEntity(entity);
		play(h, owner, idOf(entity.getItem()));
		return entity;
	}

	/** Ends the speakers' sessions, removes their drops and the players. */
	static void cleanUp(GameTestHelper h, List<UUID> ids, ServerPlayer... players) {
		for (UUID id : ids) {
			SpeakerHosts.destroyed(server(h), id);
		}
		for (ItemEntity drop : h.getEntities(EntityTypes.ITEM)) {
			drop.discard();
		}
		for (ServerPlayer player : players) {
			leave(h, player);
		}
	}

	@GameTest(maxTicks = 60)
	public void breakKeepsSession(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer owner = online(h, 2);
		UUID id = Speakers.idAt(h, pos);
		play(h, owner, id);
		int session = StreamSessions.table().byKey(id).orElseThrow().id();
		h.getLevel().destroyBlock(pos, true);
		h.runAfterDelay(25, () -> {
			h.assertTrue(SpeakerPlayback.isPlaying(id), "still playing from the drop");
			h.assertValueEqual(StreamSessions.table().byKey(id).orElseThrow().id(), session, "same session");
			SpeakerHost host = SpeakerHosts.handle(server(h), id).orElseThrow().host();
			h.assertTrue(host instanceof SpeakerHost.Dropped, "dropped host, got " + host);
			cleanUp(h, List.of(id), owner);
			h.succeed();
		});
	}

	@GameTest(maxTicks = 80)
	public void pickupByHopperPausesNotStops(GameTestHelper h) {
		BlockPos hopperPos = new BlockPos(1, 1, 1);
		BlockPos above = hopperPos.above();
		h.setBlock(hopperPos, Blocks.HOPPER);
		h.setBlock(above, ModBlocks.PORTABLE_SPEAKER);
		h.getBlockEntity(above, SpeakerBlockEntity.class).togglePower();
		ServerPlayer owner = online(h, 2);
		UUID id = Speakers.idAt(h, h.absolutePos(above));
		play(h, owner, id);
		h.getLevel().destroyBlock(h.absolutePos(above), true);
		ItemStack[] taken = new ItemStack[1];
		h.startSequence()
				.thenExecuteAfter(30, () -> {
					h.assertFalse(SpeakerPlayback.isPlaying(id), "paused once in the hopper");
					HopperBlockEntity hopper = h.getBlockEntity(hopperPos, HopperBlockEntity.class);
					for (int slot = 0; slot < hopper.getContainerSize(); slot++) {
						SpeakerData data = hopper.getItem(slot).get(ModDataComponents.SPEAKER);
						if (data != null && data.id().equals(id)) {
							taken[0] = hopper.removeItemNoUpdate(slot);
						}
					}
					if (taken[0] == null) {
						throw h.assertionException(Component.literal("The hopper holds no speaker with id " + id));
					}
					owner.getInventory().setItem(5, taken[0]);
					SpeakerHosts.carried(owner, taken[0], CarryPlace.INVENTORY);
				})
				.thenExecuteAfter(1, () -> SpeakerHosts.carried(owner, taken[0], CarryPlace.INVENTORY))
				.thenExecute(() -> {
					h.assertTrue(SpeakerPlayback.isPlaying(id), "resumed once carried");
					cleanUp(h, List.of(id), owner);
				})
				.thenSucceed();
	}

	@GameTest(maxTicks = 80)
	public void storedCarrierPausesThenResumes(GameTestHelper h) {
		ServerPlayer owner = online(h, 2);
		ItemStack stack = carriedPlaying(h, owner);
		UUID id = idOf(stack);
		h.startSequence()
				.thenIdle(1) // a second report in the setup tick would be a copy
				.thenExecuteFor(5, () -> SpeakerHosts.carried(owner, stack, CarryPlace.HAND))
				// Ticks run faster than real time here: keep carrying until the wall-clock position has moved.
				.thenWaitUntil(() -> {
					SpeakerHosts.carried(owner, stack, CarryPlace.HAND);
					SpeakerQueue stored = stack.get(ModDataComponents.SPEAKER).queue();
					h.assertTrue(SpeakerPlayback.liveQueue(id, stored).positionMs() > 0, "track still at its start");
				})
				.thenIdle(21)
				.thenExecute(() -> {
					h.assertFalse(SpeakerPlayback.isPlaying(id), "paused when no longer carried");
					SpeakerQueue parked = SpeakerHosts.parkedQueue(id).orElseThrow();
					h.assertTrue(parked.positionMs() > 0, "position kept, got " + parked.positionMs());
					SpeakerHosts.carried(owner, stack, CarryPlace.HAND);
					h.assertTrue(SpeakerPlayback.isPlaying(id), "resumed when carried again");
					cleanUp(h, List.of(id), owner);
				})
				.thenSucceed();
	}

	/** Held on the mouse cursor, where vanilla never ticks it: still found, still playing. */
	@GameTest(maxTicks = 60)
	public void cursorStackKeepsPlaying(GameTestHelper h) {
		ServerPlayer owner = online(h, 2);
		ItemStack stack = carriedPlaying(h, owner);
		UUID id = idOf(stack);
		owner.getInventory().setItem(owner.getInventory().getSelectedSlot(), ItemStack.EMPTY);
		owner.containerMenu.setCarried(stack);
		h.runAfterDelay(25, () -> {
			h.assertTrue(SpeakerPlayback.isPlaying(id), "still playing from the cursor");
			h.assertTrue(SpeakerHosts.handle(server(h), id).isPresent(), "cursor stack found");
			owner.containerMenu.setCarried(ItemStack.EMPTY);
			cleanUp(h, List.of(id), owner);
			h.succeed();
		});
	}

	/** A speaker paused in a chest must not start again on its own when its carrier comes back after leaving. */
	@GameTest(maxTicks = 60)
	public void disconnectCancelsParkedResume(GameTestHelper h) {
		ServerPlayer owner = online(h, 2);
		ItemStack stack = carriedPlaying(h, owner);
		UUID id = idOf(stack);
		h.startSequence()
				.thenIdle(21)
				.thenExecute(() -> {
					h.assertFalse(SpeakerPlayback.isPlaying(id), "paused when no longer carried");
					h.assertTrue(SpeakerHosts.parkedQueue(id).isPresent(), "parked");
					SpeakerHosts.disconnected(owner);
					SpeakerHosts.carried(owner, stack, CarryPlace.HAND);
					h.assertFalse(SpeakerPlayback.isPlaying(id), "not resumed after a disconnect");
					cleanUp(h, List.of(id), owner);
				})
				.thenSucceed();
	}

	/** Stored out of reach: the screens open on it close. */
	@GameTest(maxTicks = 60)
	public void storedCarrierClosesScreens(GameTestHelper h) {
		ServerPlayer owner = online(h, 2);
		ItemStack stack = carriedPlaying(h, owner);
		UUID id = idOf(stack);
		SpeakerWatchers.watch(owner, id, true);
		h.assertTrue(SpeakerWatchers.isWatching(id, owner.getUUID()), "screen open");
		h.runAfterDelay(22, () -> {
			h.assertTrue(SpeakerHosts.parkedQueue(id).isPresent(), "parked");
			h.assertFalse(SpeakerWatchers.isWatching(id, owner.getUUID()), "screen closed");
			cleanUp(h, List.of(id), owner);
			h.succeed();
		});
	}

	/** Burnt on the ground: the screens open on it close. */
	@GameTest
	public void destroyedClosesScreens(GameTestHelper h) {
		ServerPlayer owner = online(h, 2);
		ItemEntity drop = droppedPlaying(h, owner);
		UUID id = idOf(drop.getItem());
		SpeakerWatchers.watch(owner, id, true);
		h.assertTrue(SpeakerWatchers.isWatching(id, owner.getUUID()), "screen open");
		h.startSequence()
				.thenIdle(1) // the state sent on opening is flushed first
				.thenExecute(() -> {
					for (int i = 0; i < 10 && !drop.isRemoved(); i++) {
						drop.hurtServer(h.getLevel(), h.getLevel().damageSources().lava(), 4.0F);
					}
				})
				.thenExecuteAfter(1, () -> {
					h.assertFalse(SpeakerWatchers.isWatching(id, owner.getUUID()), "screen closed");
					cleanUp(h, List.of(id), owner);
				})
				.thenSucceed();
	}

	/** Moving the speaker between hand and pocket on successive ticks is a move of the same speaker, not a copy. */
	@GameTest(maxTicks = 40)
	public void hotbarScrollKeepsId(GameTestHelper h) {
		ServerPlayer owner = online(h, 2);
		ItemStack stack = carriedPlaying(h, owner);
		UUID id = idOf(stack);
		h.startSequence()
				.thenIdle(1) // a second report in the setup tick would be a copy
				.thenExecute(() -> SpeakerHosts.carried(owner, stack, CarryPlace.HAND))
				.thenExecuteAfter(1, () -> SpeakerHosts.carried(owner, stack, CarryPlace.INVENTORY))
				.thenExecuteAfter(1, () -> SpeakerHosts.carried(owner, stack, CarryPlace.HAND))
				.thenExecute(() -> {
					h.assertValueEqual(idOf(stack), id, "same id");
					h.assertTrue(SpeakerPlayback.isPlaying(id), "still playing");
					SpeakerHost host = SpeakerHosts.handle(server(h), id).orElseThrow().host();
					h.assertTrue(host instanceof SpeakerHost.Carrier carrier && carrier.place() == CarryPlace.HAND, "back in hand");
					cleanUp(h, List.of(id), owner);
				})
				.thenSucceed();
	}

	/** A speaker in a plain inventory slot, ticked by the inventory itself, is carried in the inventory. */
	@GameTest
	public void inventoryIsInventoryPlace(GameTestHelper h) {
		ServerPlayer owner = online(h, 2);
		ItemStack stack = speakerStack();
		owner.getInventory().setItem(INVENTORY_SLOT, stack);
		owner.getInventory().tick();
		UUID id = idOf(stack);
		SpeakerHost host = SpeakerHosts.handle(server(h), id).orElseThrow().host();
		h.assertTrue(host instanceof SpeakerHost.Carrier carrier && carrier.place() == CarryPlace.INVENTORY,
				"carried in the inventory");
		cleanUp(h, List.of(id), owner);
		h.succeed();
	}

	@GameTest
	public void duplicateInInventoryGetsNewId(GameTestHelper h) {
		ServerPlayer owner = online(h, 2);
		QueueEntry entry = new QueueEntry(UUID.randomUUID(), owner.getUUID(), "Owner", "track");
		SpeakerData data = new SpeakerData(UUID.randomUUID(), new SpeakerSettings(true, 40), SpeakerQueue.EMPTY.add(List.of(entry)));
		ItemStack first = new ItemStack(ModBlocks.PORTABLE_SPEAKER);
		ItemStack second = new ItemStack(ModBlocks.PORTABLE_SPEAKER);
		first.set(ModDataComponents.SPEAKER, data);
		second.set(ModDataComponents.SPEAKER, data);
		owner.getInventory().setItem(0, first);
		owner.getInventory().setItem(1, second);
		SpeakerHosts.carried(owner, first, CarryPlace.HAND);
		SpeakerHosts.carried(owner, second, CarryPlace.INVENTORY);
		UUID a = idOf(first);
		UUID b = idOf(second);
		h.assertTrue(a.equals(data.id()) != b.equals(data.id()), "exactly one keeps the id");
		ItemStack copy = a.equals(data.id()) ? second : first;
		h.assertValueEqual(copy.get(ModDataComponents.SPEAKER).queue(), data.queue(), "queue kept");
		cleanUp(h, List.of(a, b), owner);
		h.succeed();
	}

	@GameTest
	public void farPlayerCannotCommand(GameTestHelper h) {
		ServerPlayer owner = online(h, 2);
		ServerPlayer far = online(h, 20);
		UUID id = idOf(droppedPlaying(h, owner).getItem());
		SpeakerCommands.command(far, id, Command.PAUSE, Optional.empty());
		h.assertTrue(SpeakerPlayback.isPlaying(id), "out of reach: ignored");
		cleanUp(h, List.of(id), owner, far);
		h.succeed();
	}

	@GameTest
	public void carrierCanCommandFromAnywhere(GameTestHelper h) {
		ServerPlayer owner = online(h, 50);
		UUID id = idOf(carriedPlaying(h, owner));
		SpeakerCommands.command(owner, id, Command.PAUSE, Optional.empty());
		h.assertFalse(SpeakerPlayback.isPlaying(id), "paused by its carrier");
		cleanUp(h, List.of(id), owner);
		h.succeed();
	}

	@GameTest
	public void destroyedStops(GameTestHelper h) {
		ServerPlayer owner = online(h, 2);
		ItemEntity drop = droppedPlaying(h, owner);
		UUID id = idOf(drop.getItem());
		for (int i = 0; i < 10 && !drop.isRemoved(); i++) {
			drop.hurtServer(h.getLevel(), h.getLevel().damageSources().lava(), 4.0F);
		}
		h.assertTrue(drop.isRemoved(), "burnt");
		h.assertFalse(SpeakerPlayback.isPlaying(id), "stopped");
		h.assertTrue(SpeakerHosts.handle(server(h), id).isEmpty(), "no host left");
		cleanUp(h, List.of(id), owner);
		h.succeed();
	}

	/** In its own batch: pausing everything would pause the speakers of concurrent tests. */
	@GameTest(maxTicks = 80, environment = "ravecraft:isolated")
	public void pauseAllWritesCarriedPosition(GameTestHelper h) {
		ServerPlayer owner = online(h, 2);
		ItemStack stack = carriedPlaying(h, owner);
		UUID id = idOf(stack);
		h.startSequence()
				.thenIdle(1) // a second report in the setup tick would be a copy
				.thenExecuteFor(40, () -> SpeakerHosts.carried(owner, stack, CarryPlace.HAND))
				// Ticks run faster than real time here: wait until the wall-clock position has moved.
				.thenWaitUntil(() -> {
					SpeakerHosts.carried(owner, stack, CarryPlace.HAND);
					SpeakerQueue stored = stack.get(ModDataComponents.SPEAKER).queue();
					h.assertTrue(SpeakerPlayback.liveQueue(id, stored).positionMs() > 0, "track still at its start");
				})
				.thenExecute(() -> {
					SpeakerPlayback.pauseAll(server(h));
					long position = stack.get(ModDataComponents.SPEAKER).queue().positionMs();
					h.assertTrue(position > 0, "position written into the stack, got " + position);
					cleanUp(h, List.of(id), owner);
				})
				.thenSucceed();
	}

	@GameTest
	public void disconnectPauses(GameTestHelper h) {
		ServerPlayer owner = online(h, 2);
		UUID id = idOf(carriedPlaying(h, owner));
		SpeakerHosts.disconnected(owner);
		h.assertFalse(SpeakerPlayback.isPlaying(id), "paused when its carrier leaves");
		cleanUp(h, List.of(id), owner);
		h.succeed();
	}
}
