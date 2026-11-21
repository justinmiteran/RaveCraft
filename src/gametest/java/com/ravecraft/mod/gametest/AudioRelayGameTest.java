package com.ravecraft.mod.gametest;

import com.ravecraft.mod.blockentity.PortableSpeakerBlockEntity;
import com.ravecraft.mod.config.AcousticConfig;
import com.ravecraft.mod.core.acoustics.AcousticSettings;
import com.ravecraft.mod.core.acoustics.DeviceProfile;
import com.ravecraft.mod.core.acoustics.Directivity;
import com.ravecraft.mod.core.audio.stream.SessionTable.Session;
import com.ravecraft.mod.network.AudioRelay;
import com.ravecraft.mod.registry.ModBlocks;
import com.ravecraft.mod.world.audio.SpeakerHandle;
import com.ravecraft.mod.world.audio.SpeakerHosts;
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

	private static Optional<Session<UUID>> sessionAt(GameTestHelper h, UUID id) {
		return StreamSessions.table().byKey(id);
	}

	private static SpeakerHandle handle(GameTestHelper h, BlockPos absolute) {
		return SpeakerHosts.handle(h.getLevel().getServer(), Speakers.idAt(h, absolute)).orElseThrow();
	}

	@GameTest
	public void openInRangeCreatesSession(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer player = playerAt(h, 2);
		UUID id = Speakers.idAt(h, pos);
		Optional<Session<UUID>> opened = StreamSessions.open(handle(h, pos), player, UUID.randomUUID());
		h.assertTrue(opened.isPresent(), "session opened");
		h.assertValueEqual(sessionAt(h, id).orElseThrow().owner(), player.getUUID(), "owner");
		StreamSessions.endAt(h.getLevel().getServer(), id);
		h.succeed();
	}

	@GameTest
	public void openOnUnpoweredSpeakerRejected(GameTestHelper h) {
		h.setBlock(SPEAKER, ModBlocks.PORTABLE_SPEAKER);
		BlockPos pos = h.absolutePos(SPEAKER);
		h.assertTrue(StreamSessions.open(handle(h, pos), playerAt(h, 2), UUID.randomUUID()).isEmpty(), "rejected");
		h.assertTrue(sessionAt(h, Speakers.idAt(h, pos)).isEmpty(), "no session");
		h.succeed();
	}



	@GameTest
	public void breakingSpeakerEndsSession(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		UUID id = Speakers.idAt(h, pos);
		StreamSessions.open(handle(h, pos), playerAt(h, 2), UUID.randomUUID()).orElseThrow();
		h.destroyBlock(SPEAKER);
		h.assertTrue(sessionAt(h, id).isEmpty(), "session ended");
		h.succeed();
	}

	@GameTest
	public void poweringOffEndsSession(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		StreamSessions.open(handle(h, pos), playerAt(h, 2), UUID.randomUUID()).orElseThrow();
		h.getBlockEntity(SPEAKER, PortableSpeakerBlockEntity.class).togglePower();
		h.assertTrue(sessionAt(h, Speakers.idAt(h, pos)).isEmpty(), "session ended");
		h.succeed();
	}

	/** Sets the speaker's volume (0 to 100). */
	private static void volume(GameTestHelper h, int volume) {
		PortableSpeakerBlockEntity speaker = h.getBlockEntity(SPEAKER, PortableSpeakerBlockEntity.class);
		speaker.setData(speaker.data().withSettings(speaker.data().settings().withVolume(volume)));
	}

	private static boolean hears(GameTestHelper h, BlockPos pos, ServerPlayer owner, ServerPlayer player) {
		return AudioRelay.listeners(h.getLevel(), pos, owner.getUUID(), List.of(owner, player),
				AudioRelay.relayRadius(handle(h, pos))).contains(player);
	}

	@GameTest
	public void relayTargetsOnlyPlayersInRange(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		volume(h, 100);
		ServerPlayer owner = playerAt(h, 1);
		ServerPlayer near = playerAt(h, 5);
		ServerPlayer far = playerAt(h, 45);
		List<ServerPlayer> listeners = AudioRelay.listeners(h.getLevel(), pos, owner.getUUID(), List.of(owner, near, far),
				AudioRelay.relayRadius(handle(h, pos)));
		h.assertValueEqual(listeners, List.of(near), "listeners");
		h.succeed();
	}

	@GameTest
	public void fullVolumeReachesThirtyBlocks(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		volume(h, 100);
		h.assertTrue(hears(h, pos, playerAt(h, 1), playerAt(h, 30)), "heard at 30 blocks");
		h.succeed();
	}

	@GameTest
	public void halfVolumeDoesNotReachThirtyBlocks(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		volume(h, 50);
		h.assertFalse(hears(h, pos, playerAt(h, 1), playerAt(h, 30)), "not heard at 30 blocks");
		h.succeed();
	}

	@GameTest
	public void volumeChangeAppliesAtOnce(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		ServerPlayer owner = playerAt(h, 1);
		ServerPlayer player = playerAt(h, 30);
		volume(h, 50);
		h.assertFalse(hears(h, pos, owner, player), "not heard at half volume");
		volume(h, 100);
		h.assertTrue(hears(h, pos, owner, player), "heard at full volume");
		h.succeed();
	}

	@GameTest
	public void serverPowerSettingApplies(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		volume(h, 50);
		AcousticSettings defaults = AcousticSettings.DEFAULT;
		try {
			// Four times the power doubles the range: about 32 blocks at half volume.
			AcousticConfig.set(new AcousticSettings(defaults.rolloff(), defaults.fadeFraction(),
					defaults.audibilityThresholdDb(), defaults.maxRange(),
					new DeviceProfile(80, 85, -6, 150, Directivity.OMNI), defaults.occlusion(),
					defaults.reverb()));
			h.assertTrue(hears(h, pos, playerAt(h, 1), playerAt(h, 30)), "heard with 80 W at half volume");
		} finally {
			AcousticConfig.set(defaults);
		}
		h.succeed();
	}

	@GameTest
	public void relayRadiusIsZeroAtVolumeZero(GameTestHelper h) {
		BlockPos pos = poweredSpeaker(h);
		volume(h, 0);
		h.assertValueEqual(AudioRelay.relayRadius(handle(h, pos)), 0.0, "radius");
		h.assertFalse(hears(h, pos, playerAt(h, 5), playerAt(h, 1)), "not heard at 1 block");
		h.succeed();
	}
}
