package com.ravecraft.mod.gametest;

import com.ravecraft.mod.blockentity.PortableSpeakerBlockEntity;
import com.ravecraft.mod.component.SpeakerData;
import com.ravecraft.mod.core.audio.stream.SessionTable.Session;
import com.ravecraft.mod.core.queue.QueueEntry;
import com.ravecraft.mod.core.queue.RepeatMode;
import com.ravecraft.mod.core.queue.SpeakerQueue;
import com.ravecraft.mod.network.payload.QueueAddPayload.NewEntry;
import com.ravecraft.mod.network.payload.SpeakerCommandPayload.Command;
import com.ravecraft.mod.network.SpeakerNetwork;
import com.ravecraft.mod.registry.ModBlocks;
import com.ravecraft.mod.registry.ModDataComponents;
import com.ravecraft.mod.world.audio.SpeakerPlayback;
import com.ravecraft.mod.world.audio.StreamSessions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

public class SharedQueueGameTest {
	private static final BlockPos SPEAKER = new BlockPos(1, 1, 1);

	static BlockPos poweredSpeaker(GameTestHelper h) {
		h.setBlock(SPEAKER, ModBlocks.PORTABLE_SPEAKER);
		be(h).togglePower();
		return h.absolutePos(SPEAKER);
	}

	static PortableSpeakerBlockEntity be(GameTestHelper h) {
		return h.getBlockEntity(SPEAKER, PortableSpeakerBlockEntity.class);
	}

	static SpeakerQueue queue(GameTestHelper h) {
		return be(h).data().queue();
	}

	/** A player in the server's player list (so its entries are playable), {@code distance} blocks east. */
	static ServerPlayer online(GameTestHelper h, double distance) {
		ServerPlayer player = h.makeMockServerPlayerInLevel();
		moveTo(h, player, distance);
		return player;
	}

	private static void moveTo(GameTestHelper h, ServerPlayer player, double distance) {
		Vec3 centre = Vec3.atBottomCenterOf(h.absolutePos(SPEAKER));
		player.setPos(centre.x + distance, centre.y, centre.z);
	}

	static void leave(GameTestHelper h, ServerPlayer player) {
		h.getLevel().getServer().getPlayerList().remove(player);
		SpeakerPlayback.onDisconnect(h.getLevel().getServer(), player.getUUID());
	}

	static List<NewEntry> newEntries(int count) {
		List<NewEntry> list = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			list.add(new NewEntry(UUID.randomUUID(), "track " + i));
		}
		return list;
	}

	static Optional<Session<UUID>> session(GameTestHelper h, BlockPos pos) {
		return StreamSessions.table().byKey(Speakers.idAt(h, pos));
	}

	static int sessionId(GameTestHelper h, BlockPos pos) {
		return session(h, pos).orElseThrow().id();
	}

	static UUID entryId(GameTestHelper h, int index) {
		return queue(h).entries().get(index).entryId();
	}

	static void finish(GameTestHelper h, BlockPos pos, ServerPlayer... players) {
		SpeakerPlayback.command(players[0], Speakers.idAt(h, pos), Command.CLEAR, Optional.empty());
		for (ServerPlayer player : players) {
			leave(h, player);
		}
		h.succeed();
	}

	@GameTest
	public void addInReachAppends(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer player = online(h, 2);
		SpeakerPlayback.add(player, Speakers.idAt(h, pos), newEntries(2));
		h.assertValueEqual(queue(h).entries().size(), 2, "entries");
		QueueEntry first = queue(h).entries().getFirst();
		h.assertValueEqual(first.owner(), player.getUUID(), "owner");
		h.assertValueEqual(first.title(), "track 0", "title");
		h.assertFalse(SpeakerPlayback.isPlaying(Speakers.idAt(h, pos)), "adding does not play");
		finish(h, pos, player);
	}

	@GameTest
	public void addOutOfReachIgnored(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer player = online(h, 20);
		SpeakerPlayback.add(player, Speakers.idAt(h, pos), newEntries(2));
		h.assertTrue(queue(h).entries().isEmpty(), "ignored");
		leave(h, player);
		h.succeed();
	}

	@GameTest
	public void playStartsSessionForEntryOwner(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer owner = online(h, 2);
		ServerPlayer presser = online(h, -2);
		SpeakerPlayback.add(owner, Speakers.idAt(h, pos), newEntries(1));
		SpeakerPlayback.command(presser, Speakers.idAt(h, pos), Command.PLAY, Optional.empty());
		h.assertTrue(SpeakerPlayback.isPlaying(Speakers.idAt(h, pos)), "playing");
		h.assertValueEqual(session(h, pos).orElseThrow().owner(), owner.getUUID(), "streamed by the entry owner");
		h.assertValueEqual(queue(h).current(), Optional.of(entryId(h, 0)), "current");
		finish(h, pos, owner, presser);
	}

	@GameTest
	public void absentOwnerSkippedAndKept(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer owner = online(h, 2);
		QueueEntry absent = new QueueEntry(UUID.randomUUID(), UUID.randomUUID(), "Gone", "absent");
		be(h).setData(be(h).data().withQueue(SpeakerQueue.EMPTY.add(List.of(absent))));
		SpeakerPlayback.add(owner, Speakers.idAt(h, pos), newEntries(1));
		SpeakerPlayback.command(owner, Speakers.idAt(h, pos), Command.PLAY, Optional.empty());
		h.assertValueEqual(queue(h).current(), Optional.of(entryId(h, 1)), "skipped the absent entry");
		h.assertValueEqual(queue(h).entries().size(), 2, "absent entry kept");
		finish(h, pos, owner);
	}

	@GameTest
	public void onlyAbsentEntriesPause(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer player = online(h, 2);
		QueueEntry absent = new QueueEntry(UUID.randomUUID(), UUID.randomUUID(), "Gone", "absent");
		be(h).setData(be(h).data().withQueue(SpeakerQueue.EMPTY.add(List.of(absent))));
		SpeakerPlayback.command(player, Speakers.idAt(h, pos), Command.PLAY, Optional.empty());
		h.assertFalse(SpeakerPlayback.isPlaying(Speakers.idAt(h, pos)), "nothing playable");
		h.assertTrue(session(h, pos).isEmpty(), "no session");
		h.assertValueEqual(queue(h).entries().size(), 1, "kept");
		finish(h, pos, player);
	}

	@GameTest
	public void streamResultRemovesEntryAndAdvances(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer owner = online(h, 2);
		SpeakerPlayback.add(owner, Speakers.idAt(h, pos), newEntries(2));
		UUID second = entryId(h, 1);
		SpeakerPlayback.command(owner, Speakers.idAt(h, pos), Command.PLAY, Optional.empty());
		int first = sessionId(h, pos);
		SpeakerPlayback.onStreamResult(owner, first);
		h.assertValueEqual(queue(h).entries().size(), 1, "unreadable entry removed");
		h.assertValueEqual(queue(h).current(), Optional.of(second), "next");
		h.assertTrue(SpeakerPlayback.isPlaying(Speakers.idAt(h, pos)), "still playing");
		h.assertTrue(sessionId(h, pos) != first, "new session");
		finish(h, pos, owner);
	}

	@GameTest
	public void finishedCloseKeepsEntryAndAdvances(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer owner = online(h, 2);
		SpeakerPlayback.add(owner, Speakers.idAt(h, pos), newEntries(2));
		UUID second = entryId(h, 1);
		SpeakerPlayback.command(owner, Speakers.idAt(h, pos), Command.PLAY, Optional.empty());
		int first = sessionId(h, pos);
		SpeakerPlayback.onStreamClose(owner, first, true);
		h.assertValueEqual(queue(h).entries().size(), 2, "played entry kept in the playlist");
		h.assertValueEqual(queue(h).current(), Optional.of(second), "next");
		h.assertTrue(sessionId(h, pos) != first, "new session");
		finish(h, pos, owner);
	}

	@GameTest
	public void lastTrackFinishedPauses(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer owner = online(h, 2);
		SpeakerPlayback.add(owner, Speakers.idAt(h, pos), newEntries(1));
		SpeakerPlayback.command(owner, Speakers.idAt(h, pos), Command.REPEAT, Optional.empty(), RepeatMode.OFF.ordinal());
		SpeakerPlayback.command(owner, Speakers.idAt(h, pos), Command.PLAY, Optional.empty());
		SpeakerPlayback.onStreamClose(owner, sessionId(h, pos), true);
		h.assertValueEqual(queue(h).entries().size(), 1, "kept");
		h.assertValueEqual(queue(h).positionMs(), 0L, "back to its start");
		h.assertFalse(SpeakerPlayback.isPlaying(Speakers.idAt(h, pos)), "paused");
		finish(h, pos, owner);
	}

	@GameTest
	public void staleCloseIgnored(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer owner = online(h, 2);
		SpeakerPlayback.add(owner, Speakers.idAt(h, pos), newEntries(2));
		SpeakerPlayback.command(owner, Speakers.idAt(h, pos), Command.PLAY, Optional.empty());
		int stale = sessionId(h, pos);
		SpeakerPlayback.command(owner, Speakers.idAt(h, pos), Command.PAUSE, Optional.empty());
		SpeakerPlayback.onStreamClose(owner, stale, true);
		SpeakerPlayback.onStreamResult(owner, stale);
		h.assertValueEqual(queue(h).entries().size(), 2, "queue unchanged");
		h.assertFalse(SpeakerPlayback.isPlaying(Speakers.idAt(h, pos)), "still paused");
		finish(h, pos, owner);
	}

	@GameTest
	public void nonOwnerResultIgnored(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer owner = online(h, 2);
		ServerPlayer other = online(h, -2);
		SpeakerPlayback.add(owner, Speakers.idAt(h, pos), newEntries(2));
		SpeakerPlayback.command(owner, Speakers.idAt(h, pos), Command.PLAY, Optional.empty());
		int id = sessionId(h, pos);
		SpeakerPlayback.onStreamResult(other, id);
		SpeakerPlayback.onStreamClose(other, id, true);
		h.assertValueEqual(queue(h).entries().size(), 2, "unchanged");
		h.assertValueEqual(sessionId(h, pos), id, "same session");
		finish(h, pos, owner, other);
	}

	@GameTest
	public void removeCurrentStopsOwnerAndAdvances(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer owner = online(h, 2);
		SpeakerPlayback.add(owner, Speakers.idAt(h, pos), newEntries(2));
		UUID first = entryId(h, 0);
		UUID second = entryId(h, 1);
		SpeakerPlayback.command(owner, Speakers.idAt(h, pos), Command.PLAY, Optional.empty());
		int id = sessionId(h, pos);
		SpeakerPlayback.command(owner, Speakers.idAt(h, pos), Command.REMOVE, Optional.of(first));
		h.assertTrue(StreamSessions.table().byId(id).isEmpty(), "old session ended");
		h.assertValueEqual(queue(h).current(), Optional.of(second), "next");
		h.assertTrue(SpeakerPlayback.isPlaying(Speakers.idAt(h, pos)), "playing");
		finish(h, pos, owner);
	}

	@GameTest
	public void nextSkipsCurrent(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer owner = online(h, 2);
		SpeakerPlayback.add(owner, Speakers.idAt(h, pos), newEntries(3));
		UUID second = entryId(h, 1);
		SpeakerPlayback.command(owner, Speakers.idAt(h, pos), Command.PLAY, Optional.empty());
		SpeakerPlayback.command(owner, Speakers.idAt(h, pos), Command.NEXT, Optional.empty());
		h.assertValueEqual(queue(h).entries().size(), 3, "skipped entry kept");
		h.assertValueEqual(queue(h).current(), Optional.of(second), "next");
		h.assertTrue(SpeakerPlayback.isPlaying(Speakers.idAt(h, pos)), "playing");
		finish(h, pos, owner);
	}

	@GameTest
	public void clearEmptiesAndStops(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer owner = online(h, 2);
		SpeakerPlayback.add(owner, Speakers.idAt(h, pos), newEntries(2));
		SpeakerPlayback.command(owner, Speakers.idAt(h, pos), Command.PLAY, Optional.empty());
		SpeakerPlayback.command(owner, Speakers.idAt(h, pos), Command.CLEAR, Optional.empty());
		h.assertTrue(queue(h).entries().isEmpty(), "empty");
		h.assertTrue(session(h, pos).isEmpty(), "no session");
		h.assertFalse(SpeakerPlayback.isPlaying(Speakers.idAt(h, pos)), "stopped");
		finish(h, pos, owner);
	}

	@GameTest
	public void commandOutOfReachIgnored(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer owner = online(h, 2);
		ServerPlayer far = online(h, 20);
		SpeakerPlayback.add(owner, Speakers.idAt(h, pos), newEntries(1));
		SpeakerPlayback.command(far, Speakers.idAt(h, pos), Command.PLAY, Optional.empty());
		h.assertFalse(SpeakerPlayback.isPlaying(Speakers.idAt(h, pos)), "ignored");
		SpeakerPlayback.command(far, Speakers.idAt(h, pos), Command.CLEAR, Optional.empty());
		h.assertValueEqual(queue(h).entries().size(), 1, "ignored");
		finish(h, pos, owner, far);
	}

	@GameTest
	public void commandOnUnpoweredSpeakerDoesNotPlay(GameTestHelper h) {
		h.setBlock(SPEAKER, ModBlocks.PORTABLE_SPEAKER);
		BlockPos pos = h.absolutePos(SPEAKER);
		ServerPlayer owner = online(h, 2);
		SpeakerPlayback.add(owner, Speakers.idAt(h, pos), newEntries(1));
		SpeakerPlayback.command(owner, Speakers.idAt(h, pos), Command.PLAY, Optional.empty());
		h.assertFalse(SpeakerPlayback.isPlaying(Speakers.idAt(h, pos)), "unpowered");
		h.assertValueEqual(queue(h).entries().size(), 1, "queued anyway");
		finish(h, pos, owner);
	}

	@GameTest
	public void powerOffPausesAndStoresPosition(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer owner = online(h, 2);
		SpeakerPlayback.add(owner, Speakers.idAt(h, pos), newEntries(1));
		UUID entry = entryId(h, 0);
		SpeakerPlayback.command(owner, Speakers.idAt(h, pos), Command.PLAY, Optional.empty());
		h.runAfterDelay(10, () -> {
			be(h).togglePower();
			h.assertFalse(SpeakerPlayback.isPlaying(Speakers.idAt(h, pos)), "paused");
			h.assertTrue(session(h, pos).isEmpty(), "session ended");
			h.assertValueEqual(queue(h).current(), Optional.of(entry), "current kept");
			h.assertTrue(queue(h).positionMs() > 0, "position stored");
			finish(h, pos, owner);
		});
	}

	@GameTest
	public void breakWhilePlayingKeepsQueueInItem(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer owner = online(h, 2);
		SpeakerPlayback.add(owner, Speakers.idAt(h, pos), newEntries(2));
		SpeakerPlayback.command(owner, Speakers.idAt(h, pos), Command.PLAY, Optional.empty());
		UUID id = Speakers.idAt(h, pos);
		h.runAfterDelay(10, () -> {
			h.getLevel().destroyBlock(pos, true);
			h.assertFalse(SpeakerPlayback.isPlaying(id), "stopped");
			List<ItemEntity> drops = h.getEntities(EntityTypes.ITEM, SPEAKER, 2);
			h.assertValueEqual(drops.size(), 1, "one drop");
			SpeakerData data = drops.getFirst().getItem().get(ModDataComponents.SPEAKER);
			h.assertTrue(data != null, "data copied");
			h.assertValueEqual(data.queue().entries().size(), 2, "queue in item");
			h.assertTrue(data.queue().positionMs() > 0, "position in item");
			leave(h, owner);
			h.succeed();
		});
	}

	@GameTest
	public void ownerOutOfReachKeepsStreaming(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer owner = online(h, 2);
		SpeakerPlayback.add(owner, Speakers.idAt(h, pos), newEntries(1));
		SpeakerPlayback.command(owner, Speakers.idAt(h, pos), Command.PLAY, Optional.empty());
		int id = sessionId(h, pos);
		moveTo(h, owner, 100);
		h.runAfterDelay(5, () -> {
			h.assertTrue(SpeakerPlayback.isPlaying(Speakers.idAt(h, pos)), "still playing");
			h.assertValueEqual(sessionId(h, pos), id, "same session");
			moveTo(h, owner, 2);
			finish(h, pos, owner);
		});
	}

	@GameTest
	public void ownerLeavingSkipsToNextPlayable(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer leaver = online(h, 2);
		ServerPlayer stayer = online(h, -2);
		SpeakerPlayback.add(leaver, Speakers.idAt(h, pos), newEntries(1));
		SpeakerPlayback.add(stayer, Speakers.idAt(h, pos), newEntries(1));
		UUID stayerEntry = entryId(h, 1);
		SpeakerPlayback.command(stayer, Speakers.idAt(h, pos), Command.PLAY, Optional.empty());
		leave(h, leaver);
		h.assertValueEqual(queue(h).current(), Optional.of(stayerEntry), "moved on");
		h.assertValueEqual(session(h, pos).orElseThrow().owner(), stayer.getUUID(), "streamed by the stayer");
		h.assertValueEqual(queue(h).entries().size(), 2, "leaver's entry kept");
		finish(h, pos, stayer);
	}

	@GameTest
	public void rejoinDoesNotResume(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer owner = online(h, 2);
		ServerPlayer other = online(h, -2);
		SpeakerPlayback.add(owner, Speakers.idAt(h, pos), newEntries(1));
		SpeakerPlayback.command(owner, Speakers.idAt(h, pos), Command.PLAY, Optional.empty());
		leave(h, owner);
		h.assertFalse(SpeakerPlayback.isPlaying(Speakers.idAt(h, pos)), "paused: only an absent entry left");
		SpeakerPlayback.onJoin(other);
		h.assertFalse(SpeakerPlayback.isPlaying(Speakers.idAt(h, pos)), "a join never resumes");
		h.assertValueEqual(queue(h).entries().size(), 1, "kept");
		finish(h, pos, other);
	}

	@GameTest(maxTicks = 300)
	public void silentOwnerTimesOutAndEntryRemoved(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer owner = online(h, 2);
		SpeakerPlayback.add(owner, Speakers.idAt(h, pos), newEntries(1));
		SpeakerPlayback.command(owner, Speakers.idAt(h, pos), Command.PLAY, Optional.empty());
		h.runAfterDelay(220, () -> {
			h.assertTrue(queue(h).entries().isEmpty(), "silent entry removed");
			h.assertFalse(SpeakerPlayback.isPlaying(Speakers.idAt(h, pos)), "paused");
			finish(h, pos, owner);
		});
	}

	@GameTest
	public void savingWhilePlayingStoresLivePosition(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer owner = online(h, 2);
		SpeakerPlayback.add(owner, Speakers.idAt(h, pos), newEntries(1));
		SpeakerPlayback.command(owner, Speakers.idAt(h, pos), Command.PLAY, Optional.empty());
		h.runAfterDelay(10, () -> {
			// A chunk is saved before its block entities are removed: the save itself must carry the position.
			CompoundTag tag = be(h).saveWithoutMetadata(h.getLevel().registryAccess());
			SpeakerData saved = SpeakerData.CODEC.parse(NbtOps.INSTANCE, tag.get(PortableSpeakerBlockEntity.KEY)).getOrThrow();
			h.assertTrue(saved.queue().positionMs() > 0, "live position saved");
			finish(h, pos, owner);
		});
	}

	@GameTest(maxTicks = 300)
	public void lateResumeGetsLongerStartTimeout(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer owner = online(h, 2);
		SpeakerPlayback.add(owner, Speakers.idAt(h, pos), newEntries(1));
		SpeakerQueue queued = queue(h);
		// One hour into a mix: the owner's client decodes and drops that hour before sending anything.
		be(h).setData(be(h).data().withQueue(queued.withCurrent(queued.entries().getFirst().entryId(), 3_600_000)));
		SpeakerPlayback.command(owner, Speakers.idAt(h, pos), Command.PLAY, Optional.empty());
		h.runAfterDelay(220, () -> {
			h.assertValueEqual(queue(h).entries().size(), 1, "entry kept while the owner skips");
			h.assertTrue(SpeakerPlayback.isPlaying(Speakers.idAt(h, pos)), "still playing");
			finish(h, pos, owner);
		});
	}

	@GameTest
	public void commandsAreRateLimited(GameTestHelper h) {
		ServerPlayer player = online(h, 2);
		for (int i = 0; i < SpeakerNetwork.COMMANDS_PER_SECOND; i++) {
			h.assertTrue(SpeakerNetwork.allowCommand(player), "command " + i + " allowed");
		}
		h.assertFalse(SpeakerNetwork.allowCommand(player), "burst over the limit dropped");
		leave(h, player);
		h.succeed();
	}

	private static List<String> order(GameTestHelper h) {
		return queue(h).displayOrder().stream().map(QueueEntry::title).toList();
	}

	@GameTest
	public void moveReordersQueue(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer player = online(h, 2);
		SpeakerPlayback.add(player, Speakers.idAt(h, pos), newEntries(3));
		SpeakerPlayback.command(player, Speakers.idAt(h, pos), Command.PLAY, Optional.empty());
		SpeakerPlayback.command(player, Speakers.idAt(h, pos), Command.MOVE, Optional.of(entryId(h, 2)), 0);
		h.assertValueEqual(order(h), List.of("track 0", "track 2", "track 1"), "moved to the head of what is next");
		finish(h, pos, player);
	}

	@GameTest
	public void moveUnknownOrHugeIndexIsHarmless(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer player = online(h, 2);
		SpeakerPlayback.add(player, Speakers.idAt(h, pos), newEntries(4));
		SpeakerPlayback.command(player, Speakers.idAt(h, pos), Command.PLAY, Optional.empty());
		SpeakerPlayback.command(player, Speakers.idAt(h, pos), Command.MOVE, Optional.of(UUID.randomUUID()), 0);
		h.assertValueEqual(order(h), List.of("track 0", "track 1", "track 2", "track 3"), "unknown id ignored");
		UUID second = entryId(h, 1);
		SpeakerPlayback.command(player, Speakers.idAt(h, pos), Command.MOVE, Optional.of(second), Long.MAX_VALUE);
		h.assertValueEqual(order(h), List.of("track 0", "track 2", "track 3", "track 1"), "huge index puts it last");
		SpeakerPlayback.command(player, Speakers.idAt(h, pos), Command.MOVE, Optional.of(second), -3);
		h.assertValueEqual(order(h), List.of("track 0", "track 1", "track 2", "track 3"), "negative index puts it first");
		finish(h, pos, player);
	}

	@GameTest
	public void moveOutOfReachIgnored(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer near = online(h, 2);
		ServerPlayer far = online(h, 20);
		SpeakerPlayback.add(near, Speakers.idAt(h, pos), newEntries(3));
		SpeakerPlayback.command(far, Speakers.idAt(h, pos), Command.MOVE, Optional.of(entryId(h, 2)), 0);
		h.assertValueEqual(order(h), List.of("track 0", "track 1", "track 2"), "ignored out of reach");
		finish(h, pos, near, far);
	}
}
