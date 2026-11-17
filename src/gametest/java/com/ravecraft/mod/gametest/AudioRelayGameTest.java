package com.ravecraft.mod.gametest;

import com.ravecraft.mod.blockentity.PortableSpeakerBlockEntity;
import com.ravecraft.mod.core.audio.stream.SessionTable.Session;
import com.ravecraft.mod.network.AudioRelay;
import com.ravecraft.mod.registry.ModBlocks;
import com.ravecraft.mod.world.audio.SpeakerKey;
import com.ravecraft.mod.world.audio.StreamSessions;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public class AudioRelayGameTest {
	private static final BlockPos SPEAKER = new BlockPos(1, 1, 1);

	private static BlockPos poweredSpeaker(GameTestHelper h) {
		h.setBlock(SPEAKER, ModBlocks.PORTABLE_SPEAKER);
		h.getBlockEntity(SPEAKER, PortableSpeakerBlockEntity.class).togglePower();
		return h.absolutePos(SPEAKER);
	}

	/** A survival server player standing {@code distance} blocks east of the speaker. */
	private static ServerPlayer playerAt(GameTestHelper h, double distance) {
		ServerPlayer player = (ServerPlayer) h.makeMockServerPlayer(GameType.SURVIVAL);
		Vec3 centre = Vec3.atBottomCenterOf(h.absolutePos(SPEAKER));
		player.setPos(centre.x + distance, centre.y, centre.z);
		return player;
	}

	private static Optional<Session<SpeakerKey>> sessionAt(GameTestHelper h, BlockPos absolute) {
		return StreamSessions.table().byKey(new SpeakerKey(h.getLevel().dimension(), absolute));
	}

	@GameTest
	public void openInRangeCreatesSession(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer player = playerAt(h, 2);
		Optional<Session<SpeakerKey>> opened = StreamSessions.open(h.getLevel(), pos, player, UUID.randomUUID());
		h.assertTrue(opened.isPresent(), "session opened");
		h.assertValueEqual(sessionAt(h, pos).orElseThrow().owner(), player.getUUID(), "owner");
		StreamSessions.endAt(h.getLevel(), pos);
		h.succeed();
	}

	@GameTest
	public void openOnUnpoweredSpeakerRejected(GameTestHelper h) {
		h.setBlock(SPEAKER, ModBlocks.PORTABLE_SPEAKER);
		BlockPos pos = h.absolutePos(SPEAKER);
		h.assertTrue(StreamSessions.open(h.getLevel(), pos, playerAt(h, 2), UUID.randomUUID()).isEmpty(), "rejected");
		h.assertTrue(sessionAt(h, pos).isEmpty(), "no session");
		h.succeed();
	}



	@GameTest
	public void breakingSpeakerEndsSession(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		StreamSessions.open(h.getLevel(), pos, playerAt(h, 2), UUID.randomUUID()).orElseThrow();
		h.destroyBlock(SPEAKER);
		h.assertTrue(sessionAt(h, pos).isEmpty(), "session ended");
		h.succeed();
	}

	@GameTest
	public void poweringOffEndsSession(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		StreamSessions.open(h.getLevel(), pos, playerAt(h, 2), UUID.randomUUID()).orElseThrow();
		h.getBlockEntity(SPEAKER, PortableSpeakerBlockEntity.class).togglePower();
		h.assertTrue(sessionAt(h, pos).isEmpty(), "session ended");
		h.succeed();
	}

	@GameTest
	public void relayTargetsOnlyPlayersInRange(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer owner = playerAt(h, 1);
		ServerPlayer near = playerAt(h, 5);
		ServerPlayer far = playerAt(h, 30);
		List<ServerPlayer> listeners = AudioRelay.listeners(h.getLevel(), pos, owner.getUUID(), List.of(owner, near, far));
		h.assertValueEqual(listeners, List.of(near), "listeners");
		h.succeed();
	}

}
