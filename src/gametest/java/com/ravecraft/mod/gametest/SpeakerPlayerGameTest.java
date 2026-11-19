package com.ravecraft.mod.gametest;

import static com.ravecraft.mod.gametest.SharedQueueGameTest.be;
import static com.ravecraft.mod.gametest.SharedQueueGameTest.entryId;
import static com.ravecraft.mod.gametest.SharedQueueGameTest.finish;
import static com.ravecraft.mod.gametest.SharedQueueGameTest.leave;
import static com.ravecraft.mod.gametest.SharedQueueGameTest.newEntries;
import static com.ravecraft.mod.gametest.SharedQueueGameTest.online;
import static com.ravecraft.mod.gametest.SharedQueueGameTest.poweredSpeaker;
import static com.ravecraft.mod.gametest.SharedQueueGameTest.queue;
import static com.ravecraft.mod.gametest.SharedQueueGameTest.sessionId;

import com.ravecraft.mod.core.queue.QueueEntry;
import com.ravecraft.mod.core.queue.RepeatMode;
import com.ravecraft.mod.core.queue.SpeakerQueue;
import com.ravecraft.mod.network.SpeakerNetwork;
import com.ravecraft.mod.network.payload.SpeakerCommandPayload.Command;
import com.ravecraft.mod.world.audio.SpeakerPlayback;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

public class SpeakerPlayerGameTest {
	private static void command(ServerPlayer player, BlockPos pos, Command command, long value) {
		SpeakerPlayback.command(player, pos, command, Optional.empty(), value);
	}

	/** Makes entry {@code index} current at {@code positionMs} without playing, optionally with a known duration. */
	private static void setCurrent(GameTestHelper h, int index, long positionMs, long durationMs) {
		SpeakerQueue queued = queue(h);
		UUID id = queued.entries().get(index).entryId();
		SpeakerQueue changed = queued.withCurrent(id, positionMs);
		if (durationMs != QueueEntry.UNKNOWN_DURATION) {
			changed = changed.withDuration(id, durationMs);
		}
		be(h).setData(be(h).data().withQueue(changed));
	}

	@GameTest
	public void naturalEndKeepsEntryAndAdvances(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer owner = online(h, 2);
		SpeakerPlayback.add(owner, pos, newEntries(2));
		UUID second = entryId(h, 1);
		SpeakerPlayback.command(owner, pos, Command.PLAY, Optional.empty());
		int first = sessionId(h, pos);
		SpeakerPlayback.onStreamClose(owner, first, true);
		h.assertValueEqual(queue(h).entries().size(), 2, "played entry kept");
		h.assertValueEqual(queue(h).current(), Optional.of(second), "next");
		h.assertTrue(SpeakerPlayback.isPlaying(h.getLevel(), pos), "playing");
		h.assertTrue(sessionId(h, pos) != first, "new session");
		finish(h, pos, owner);
	}

	@GameTest
	public void repeatOffStopsAfterLast(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer owner = online(h, 2);
		SpeakerPlayback.add(owner, pos, newEntries(2));
		UUID second = entryId(h, 1);
		command(owner, pos, Command.REPEAT, RepeatMode.OFF.ordinal());
		SpeakerPlayback.command(owner, pos, Command.PLAY, Optional.empty());
		SpeakerPlayback.onStreamClose(owner, sessionId(h, pos), true);
		h.assertValueEqual(queue(h).current(), Optional.of(second), "second");
		SpeakerPlayback.onStreamClose(owner, sessionId(h, pos), true);
		h.assertFalse(SpeakerPlayback.isPlaying(h.getLevel(), pos), "paused after the last");
		h.assertValueEqual(queue(h).current(), Optional.of(second), "cursor on the last");
		h.assertValueEqual(queue(h).positionMs(), 0L, "from its start");
		h.assertValueEqual(queue(h).entries().size(), 2, "entries kept");
		finish(h, pos, owner);
	}

	@GameTest
	public void previousAfterThreeSecondsRestarts(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer owner = online(h, 2);
		SpeakerPlayback.add(owner, pos, newEntries(2));
		UUID second = entryId(h, 1);
		SpeakerPlayback.command(owner, pos, Command.PLAY, Optional.empty());
		// NEXT fills the history, so going back would be possible under 3 s.
		SpeakerPlayback.command(owner, pos, Command.NEXT, Optional.empty());
		// The session clock starts at the stored position: 5 s into the second entry.
		SpeakerPlayback.command(owner, pos, Command.PAUSE, Optional.empty());
		setCurrent(h, 1, 5_000, QueueEntry.UNKNOWN_DURATION);
		SpeakerPlayback.command(owner, pos, Command.PLAY, Optional.empty());
		int before = sessionId(h, pos);
		SpeakerPlayback.command(owner, pos, Command.PREVIOUS, Optional.empty());
		h.assertValueEqual(queue(h).current(), Optional.of(second), "same entry");
		h.assertValueEqual(queue(h).positionMs(), 0L, "from its start");
		h.assertTrue(SpeakerPlayback.isPlaying(h.getLevel(), pos), "playing");
		h.assertTrue(sessionId(h, pos) != before, "restarted");
		finish(h, pos, owner);
	}

	@GameTest
	public void previousEarlyGoesBack(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer owner = online(h, 2);
		SpeakerPlayback.add(owner, pos, newEntries(3));
		UUID first = entryId(h, 0);
		UUID second = entryId(h, 1);
		SpeakerPlayback.command(owner, pos, Command.PLAY, Optional.empty());
		SpeakerPlayback.command(owner, pos, Command.NEXT, Optional.empty());
		h.assertValueEqual(queue(h).current(), Optional.of(second), "moved on");
		SpeakerPlayback.command(owner, pos, Command.PREVIOUS, Optional.empty());
		h.assertValueEqual(queue(h).current(), Optional.of(first), "back");
		h.assertValueEqual(queue(h).positionMs(), 0L, "from its start");
		h.assertTrue(SpeakerPlayback.isPlaying(h.getLevel(), pos), "playing");
		// History empty now: previous restarts the current entry.
		SpeakerPlayback.command(owner, pos, Command.PREVIOUS, Optional.empty());
		h.assertValueEqual(queue(h).current(), Optional.of(first), "stays");
		finish(h, pos, owner);
	}

	@GameTest
	public void seekWithoutDurationIgnored(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer owner = online(h, 2);
		SpeakerPlayback.add(owner, pos, newEntries(1));
		setCurrent(h, 0, 0, QueueEntry.UNKNOWN_DURATION);
		command(owner, pos, Command.SEEK, 4_000);
		h.assertValueEqual(queue(h).positionMs(), 0L, "unchanged");
		finish(h, pos, owner);
	}

	@GameTest
	public void seekWhilePausedStoresPosition(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer owner = online(h, 2);
		SpeakerPlayback.add(owner, pos, newEntries(1));
		setCurrent(h, 0, 0, 10_000);
		command(owner, pos, Command.SEEK, 4_000);
		h.assertValueEqual(queue(h).positionMs(), 4_000L, "stored");
		h.assertFalse(SpeakerPlayback.isPlaying(h.getLevel(), pos), "still paused");
		finish(h, pos, owner);
	}

	@GameTest
	public void seekClampsToDuration(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer owner = online(h, 2);
		SpeakerPlayback.add(owner, pos, newEntries(1));
		SpeakerPlayback.command(owner, pos, Command.PLAY, Optional.empty());
		int first = sessionId(h, pos);
		SpeakerPlayback.onStreamStarted(owner, first, 10_000);
		command(owner, pos, Command.SEEK, 99_000);
		h.assertValueEqual(queue(h).positionMs(), 10_000L, "clamped to the duration");
		h.assertTrue(SpeakerPlayback.isPlaying(h.getLevel(), pos), "still playing");
		h.assertTrue(sessionId(h, pos) != first, "restreamed from the new position");
		command(owner, pos, Command.SEEK, -50);
		h.assertValueEqual(queue(h).positionMs(), 0L, "clamped to zero");
		finish(h, pos, owner);
	}

	@GameTest
	public void volumeClampedTo100(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer player = online(h, 2);
		command(player, pos, Command.VOLUME, 250);
		h.assertValueEqual(be(h).data().settings().volume(), 100, "clamped high");
		command(player, pos, Command.VOLUME, -3);
		h.assertValueEqual(be(h).data().settings().volume(), 0, "clamped low");
		command(player, pos, Command.VOLUME, 42);
		h.assertValueEqual(be(h).data().settings().volume(), 42, "set");
		finish(h, pos, player);
	}

	@GameTest
	public void powerCommandTurnsOff(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer owner = online(h, 2);
		SpeakerPlayback.add(owner, pos, newEntries(1));
		SpeakerPlayback.command(owner, pos, Command.PLAY, Optional.empty());
		command(owner, pos, Command.POWER, 0);
		h.assertFalse(be(h).data().settings().powered(), "off");
		h.assertFalse(SpeakerPlayback.isPlaying(h.getLevel(), pos), "paused");
		command(owner, pos, Command.POWER, 0);
		h.assertFalse(be(h).data().settings().powered(), "still off: not a toggle");
		command(owner, pos, Command.POWER, 1);
		h.assertTrue(be(h).data().settings().powered(), "on again");
		finish(h, pos, owner);
	}

	@GameTest
	public void repeatOutOfRangeIgnored(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer player = online(h, 2);
		command(player, pos, Command.REPEAT, RepeatMode.ONE.ordinal());
		h.assertValueEqual(queue(h).repeat(), RepeatMode.ONE, "set");
		command(player, pos, Command.REPEAT, 3);
		command(player, pos, Command.REPEAT, -1);
		h.assertValueEqual(queue(h).repeat(), RepeatMode.ONE, "unchanged");
		finish(h, pos, player);
	}

	@GameTest
	public void streamStartedStoresDuration(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer owner = online(h, 2);
		ServerPlayer other = online(h, -2);
		SpeakerPlayback.add(owner, pos, newEntries(1));
		SpeakerPlayback.command(owner, pos, Command.PLAY, Optional.empty());
		int id = sessionId(h, pos);
		SpeakerPlayback.onStreamStarted(other, id, 1_000);
		h.assertValueEqual(queue(h).entries().getFirst().durationMs(), QueueEntry.UNKNOWN_DURATION, "not the owner");
		SpeakerPlayback.onStreamStarted(owner, id, 215_000);
		h.assertValueEqual(queue(h).entries().getFirst().durationMs(), 215_000L, "stored");
		SpeakerPlayback.onStreamStarted(owner, id, -1);
		h.assertValueEqual(queue(h).entries().getFirst().durationMs(), 215_000L, "unknown ignored");
		finish(h, pos, owner, other);
	}

	@GameTest
	public void volumeCommandsHaveTheirOwnLimit(GameTestHelper h) {
		ServerPlayer player = online(h, 2);
		for (int i = 0; i < SpeakerNetwork.VOLUMES_PER_SECOND; i++) {
			h.assertTrue(SpeakerNetwork.allowVolume(player), "volume " + i + " allowed");
		}
		h.assertFalse(SpeakerNetwork.allowVolume(player), "burst over the volume limit dropped");
		h.assertTrue(SpeakerNetwork.allowCommand(player), "other commands keep their budget");
		leave(h, player);
		h.succeed();
	}
}
