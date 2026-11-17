package com.ravecraft.mod.network.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

class QueuePayloadsTest {
	private static final BlockPos POS = new BlockPos(3, 64, -7);

	private static <T> T roundTrip(StreamCodec<ByteBuf, T> codec, T value) {
		ByteBuf buf = Unpooled.buffer();
		codec.encode(buf, value);
		T decoded = codec.decode(buf);
		assertEquals(0, buf.readableBytes(), "fully read");
		return decoded;
	}

	private static List<QueueAddPayload.NewEntry> newEntries(int count) {
		List<QueueAddPayload.NewEntry> list = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			list.add(new QueueAddPayload.NewEntry(UUID.randomUUID(), "track " + i));
		}
		return list;
	}

	private static List<SpeakerStatePayload.StateEntry> stateEntries(int count) {
		List<SpeakerStatePayload.StateEntry> list = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			list.add(new SpeakerStatePayload.StateEntry(UUID.randomUUID(), "Alex", "track " + i, i % 2 == 0));
		}
		return list;
	}

	@Test
	void queueAddRoundTrips() {
		QueueAddPayload payload = new QueueAddPayload(POS, newEntries(50));
		assertEquals(payload, roundTrip(QueueAddPayload.CODEC, payload));
	}

	@Test
	void commandRoundTrips() {
		for (SpeakerCommandPayload.Command command : SpeakerCommandPayload.Command.values()) {
			SpeakerCommandPayload payload = new SpeakerCommandPayload(POS, command, Optional.of(UUID.randomUUID()));
			assertEquals(payload, roundTrip(SpeakerCommandPayload.CODEC, payload));
		}
		SpeakerCommandPayload noEntry = new SpeakerCommandPayload(POS, SpeakerCommandPayload.Command.PLAY, Optional.empty());
		assertEquals(noEntry, roundTrip(SpeakerCommandPayload.CODEC, noEntry));
	}

	@Test
	void watchRoundTrips() {
		SpeakerWatchPayload payload = new SpeakerWatchPayload(POS, true);
		assertEquals(payload, roundTrip(SpeakerWatchPayload.CODEC, payload));
	}

	@Test
	void stateRoundTrips() {
		SpeakerStatePayload payload = new SpeakerStatePayload(POS, true, Optional.of(UUID.randomUUID()), 90_000L, stateEntries(200));
		assertEquals(payload, roundTrip(SpeakerStatePayload.CODEC, payload));
	}

	@Test
	void streamOrderRoundTrips() {
		// The dimension travels with the order: the owner's client must not loop its stream back elsewhere.
		var nether = ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("the_nether"));
		StreamOrderPayload payload = new StreamOrderPayload(12, nether, POS, true, UUID.randomUUID(), 61_250L);
		StreamOrderPayload read = roundTrip(StreamOrderPayload.CODEC, payload);
		assertEquals(payload, read);
		assertEquals(nether, read.dimension());
	}

	@Test
	void streamResultRoundTrips() {
		StreamResultPayload payload = new StreamResultPayload(42);
		assertEquals(payload, roundTrip(StreamResultPayload.CODEC, payload));
	}

	@Test
	void trackRemovedRoundTrips() {
		TrackRemovedPayload payload = new TrackRemovedPayload("My Song");
		assertEquals(payload, roundTrip(TrackRemovedPayload.CODEC, payload));
	}

	@Test
	void queueAddOver50Throws() {
		assertEquals(50, QueueAddPayload.MAX_ENTRIES);
		assertThrows(RuntimeException.class,
				() -> QueueAddPayload.CODEC.encode(Unpooled.buffer(), new QueueAddPayload(POS, newEntries(51))));
	}

	@Test
	void stateOver200Throws() {
		assertEquals(200, SpeakerStatePayload.MAX_ENTRIES);
		assertThrows(RuntimeException.class, () -> SpeakerStatePayload.CODEC.encode(Unpooled.buffer(),
				new SpeakerStatePayload(POS, false, Optional.empty(), 0, stateEntries(201))));
	}

	@Test
	void commandWithBadOrdinalThrowsOnDecode() {
		ByteBuf buf = Unpooled.buffer();
		BlockPos.STREAM_CODEC.encode(buf, POS);
		VarInt.write(buf, 9);
		buf.writeBoolean(false);
		assertThrows(RuntimeException.class, () -> SpeakerCommandPayload.CODEC.decode(buf));
	}

	@Test
	void titleOver128Throws() {
		List<QueueAddPayload.NewEntry> entries = List.of(new QueueAddPayload.NewEntry(UUID.randomUUID(), "x".repeat(129)));
		assertThrows(RuntimeException.class,
				() -> QueueAddPayload.CODEC.encode(Unpooled.buffer(), new QueueAddPayload(POS, entries)));
		assertThrows(RuntimeException.class,
				() -> TrackRemovedPayload.CODEC.encode(Unpooled.buffer(), new TrackRemovedPayload("x".repeat(129))));
	}
}
