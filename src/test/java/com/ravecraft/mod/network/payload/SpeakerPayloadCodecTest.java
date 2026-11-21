package com.ravecraft.mod.network.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ravecraft.mod.core.queue.RepeatMode;
import com.ravecraft.mod.network.payload.SpeakerCommandPayload.Command;
import com.ravecraft.mod.network.payload.SpeakerStatePayload.StateEntry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import org.junit.jupiter.api.Test;

class SpeakerPayloadCodecTest {
	private static final BlockPos POS = new BlockPos(3, 64, -7);
	private static final UUID SPEAKER = UUID.fromString("8a3c1f52-0d7e-4b6a-9c21-5e4f7a6b3d10");

	private static <T> T roundTrip(StreamCodec<ByteBuf, T> codec, T value) {
		ByteBuf buf = Unpooled.buffer();
		codec.encode(buf, value);
		T decoded = codec.decode(buf);
		assertEquals(0, buf.readableBytes(), "fully read");
		return decoded;
	}

	private static List<StateEntry> entries(int count) {
		List<StateEntry> list = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			list.add(new StateEntry(UUID.randomUUID(), "Alex", "track " + i, i % 2 == 0, 1000L * i));
		}
		return list;
	}

	@Test
	void commandRoundTripsValue() {
		SpeakerCommandPayload payload = new SpeakerCommandPayload(SPEAKER, Command.SEEK, Optional.empty(), 93_000L);
		SpeakerCommandPayload read = roundTrip(SpeakerCommandPayload.CODEC, payload);
		assertEquals(payload, read);
		assertEquals(93_000L, read.value());
		assertEquals(0L, new SpeakerCommandPayload(SPEAKER, Command.PLAY, Optional.empty()).value());
	}

	@Test
	void stateRoundTripsModes() {
		SpeakerStatePayload payload = new SpeakerStatePayload(SPEAKER, true, Optional.of(UUID.randomUUID()), 90_000L,
				entries(5), RepeatMode.ONE, true, 35, true, 3);
		SpeakerStatePayload read = roundTrip(SpeakerStatePayload.CODEC, payload);
		assertEquals(payload, read);
		assertEquals(RepeatMode.ONE, read.repeat());
		assertEquals(3, read.nextRoundIndex());
	}

	@Test
	void stateCapsEntriesAndSeparator() {
		SpeakerStatePayload payload = new SpeakerStatePayload(SPEAKER, false, Optional.empty(), 0L, entries(250),
				RepeatMode.ALL, false, 70, false, 230);
		SpeakerStatePayload read = roundTrip(SpeakerStatePayload.CODEC, payload);
		assertEquals(200, read.entries().size());
		assertEquals(-1, read.nextRoundIndex());
	}

	@Test
	void closedRoundTripsId() {
		SpeakerClosedPayload payload = new SpeakerClosedPayload(SPEAKER);
		assertEquals(payload, roundTrip(SpeakerClosedPayload.CODEC, payload));
	}

	@Test
	void streamStartedRoundTrips() {
		StreamStartedPayload payload = new StreamStartedPayload(12, 215_000L);
		assertEquals(payload, roundTrip(StreamStartedPayload.CODEC, payload));
	}

	@Test
	void nowPlayingRoundTrips() {
		NowPlayingPayload payload = new NowPlayingPayload(7, POS, UUID.randomUUID(), "Artist — Title", "Steve");
		assertEquals(payload, roundTrip(NowPlayingPayload.CODEC, payload));
	}
}
