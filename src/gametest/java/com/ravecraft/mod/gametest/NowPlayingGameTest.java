package com.ravecraft.mod.gametest;

import static com.ravecraft.mod.gametest.SharedQueueGameTest.finish;
import static com.ravecraft.mod.gametest.SharedQueueGameTest.newEntries;
import static com.ravecraft.mod.gametest.SharedQueueGameTest.online;
import static com.ravecraft.mod.gametest.SharedQueueGameTest.poweredSpeaker;
import static com.ravecraft.mod.gametest.SharedQueueGameTest.sessionId;

import com.ravecraft.mod.network.payload.NowPlayingPayload;
import com.ravecraft.mod.network.payload.SpeakerCommandPayload.Command;
import com.ravecraft.mod.world.audio.NowPlaying;
import com.ravecraft.mod.world.audio.SpeakerPlayback;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

public class NowPlayingGameTest {
	@GameTest
	public void noticeOpenedOnPlay(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer player = online(h, 2);
		SpeakerPlayback.add(player, Speakers.idAt(h, pos), newEntries(1));
		SpeakerPlayback.command(player, Speakers.idAt(h, pos), Command.PLAY, Optional.empty());
		NowPlayingPayload notice = NowPlaying.of(sessionId(h, pos)).orElseThrow();
		h.assertValueEqual(notice.title(), "track 0", "title");
		h.assertValueEqual(notice.ownerName(), player.getName().getString(), "owner name");
		h.assertValueEqual(notice.pos(), pos, "speaker");
		finish(h, pos, player);
	}

	@GameTest
	public void noticeDroppedWhenSessionEnds(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer player = online(h, 2);
		SpeakerPlayback.add(player, Speakers.idAt(h, pos), newEntries(1));
		SpeakerPlayback.command(player, Speakers.idAt(h, pos), Command.PLAY, Optional.empty());
		int id = sessionId(h, pos);
		SpeakerPlayback.command(player, Speakers.idAt(h, pos), Command.CLEAR, Optional.empty());
		h.assertTrue(NowPlaying.of(id).isEmpty(), "notice dropped");
		finish(h, pos, player);
	}
}
