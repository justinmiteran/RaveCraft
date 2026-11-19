package com.ravecraft.mod.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.ravecraft.mod.core.queue.QueueEntry;
import com.ravecraft.mod.core.queue.RepeatMode;
import com.ravecraft.mod.core.queue.SpeakerQueue;
import com.ravecraft.mod.core.speaker.SpeakerSettings;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import org.junit.jupiter.api.Test;

class SpeakerDataTest {
	private static QueueEntry entry(String title) {
		return new QueueEntry(UUID.randomUUID(), UUID.randomUUID(), "Alex", title);
	}

	private static SpeakerData withTwoEntries() {
		QueueEntry a = entry("first");
		QueueEntry b = entry("second");
		SpeakerQueue queue = SpeakerQueue.EMPTY.add(List.of(a, b)).withCurrent(b.entryId(), 12_345);
		return new SpeakerData(UUID.randomUUID(), new SpeakerSettings(true, 40), queue);
	}

	@Test
	void decodesWithoutQueueField() {
		UUID id = UUID.randomUUID();
		JsonObject json = new JsonObject();
		json.add("id", UUIDUtil.CODEC.encodeStart(JsonOps.INSTANCE, id).getOrThrow());
		json.addProperty("powered", true);
		json.addProperty("volume", 40);
		SpeakerData data = SpeakerData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(id, data.id());
		assertEquals(new SpeakerSettings(true, 40), data.settings());
		assertEquals(SpeakerQueue.EMPTY, data.queue());
	}

	@Test
	void queueRoundTripsThroughCodec() {
		SpeakerData data = withTwoEntries();
		JsonElement encoded = SpeakerData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
		assertEquals(data, SpeakerData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
	}

	@Test
	void queueRoundTripsThroughStreamCodec() {
		SpeakerData data = withTwoEntries();
		ByteBuf buf = Unpooled.buffer();
		SpeakerData.STREAM_CODEC.encode(buf, data);
		assertEquals(data, SpeakerData.STREAM_CODEC.decode(buf));
	}

	@Test
	void decodeTruncatesQueueOver200() {
		JsonObject json = SpeakerData.CODEC.encodeStart(JsonOps.INSTANCE, withTwoEntries()).getOrThrow().getAsJsonObject();
		JsonObject queue = json.getAsJsonObject("queue");
		JsonArray entries = new JsonArray();
		for (int i = 0; i < 210; i++) {
			entries.add(QueueCodecs.ENTRY.encodeStart(JsonOps.INSTANCE, entry("t" + i)).getOrThrow());
		}
		queue.add("entries", entries);
		SpeakerData data = SpeakerData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(SpeakerQueue.MAX_ENTRIES, data.queue().entries().size());
	}

	@Test
	void loadsQueueWithoutStep5Fields() {
		UUID id = UUID.randomUUID();
		JsonObject entry = new JsonObject();
		entry.add("id", UUIDUtil.CODEC.encodeStart(JsonOps.INSTANCE, id).getOrThrow());
		entry.add("owner", UUIDUtil.CODEC.encodeStart(JsonOps.INSTANCE, UUID.randomUUID()).getOrThrow());
		entry.addProperty("owner_name", "Alex");
		entry.addProperty("title", "old");
		JsonArray entries = new JsonArray();
		entries.add(entry);
		JsonObject queueJson = new JsonObject();
		queueJson.add("entries", entries);
		queueJson.add("current", UUIDUtil.CODEC.encodeStart(JsonOps.INSTANCE, id).getOrThrow());
		queueJson.addProperty("position_ms", 5L);
		SpeakerQueue queue = QueueCodecs.QUEUE.parse(JsonOps.INSTANCE, queueJson).getOrThrow();
		assertEquals(RepeatMode.ALL, queue.repeat());
		assertEquals(false, queue.shuffle());
		assertTrue(queue.pending().isEmpty());
		assertEquals(QueueEntry.UNKNOWN_DURATION, queue.entries().get(0).durationMs());
		assertEquals(5L, queue.positionMs());
	}

	private static SpeakerQueue withModes() {
		QueueEntry a = new QueueEntry(UUID.randomUUID(), UUID.randomUUID(), "Alex", "a", 61_000);
		QueueEntry b = new QueueEntry(UUID.randomUUID(), UUID.randomUUID(), "Alex", "b", QueueEntry.UNKNOWN_DURATION);
		return new SpeakerQueue(List.of(a, b), Optional.of(a.entryId()), 7, RepeatMode.ONE, true, List.of(b.entryId()));
	}

	@Test
	void roundTripsModesAndDurations() {
		SpeakerQueue queue = withModes();
		JsonElement json = QueueCodecs.QUEUE.encodeStart(JsonOps.INSTANCE, queue).getOrThrow();
		assertEquals(queue, QueueCodecs.QUEUE.parse(JsonOps.INSTANCE, json).getOrThrow());
		ByteBuf buf = Unpooled.buffer();
		QueueCodecs.QUEUE_STREAM.encode(buf, queue);
		assertEquals(queue, QueueCodecs.QUEUE_STREAM.decode(buf));
		for (RepeatMode mode : RepeatMode.values()) {
			SpeakerQueue q = new SpeakerQueue(List.of(), Optional.empty(), 0, mode, false, List.of());
			ByteBuf b = Unpooled.buffer();
			QueueCodecs.QUEUE_STREAM.encode(b, q);
			assertEquals(q, QueueCodecs.QUEUE_STREAM.decode(b));
		}
	}

	@Test
	void unknownRepeatFallsBackToAll() {
		JsonObject json = QueueCodecs.QUEUE.encodeStart(JsonOps.INSTANCE, withModes()).getOrThrow().getAsJsonObject();
		json.addProperty("repeat", "bogus");
		assertEquals(RepeatMode.ALL, QueueCodecs.QUEUE.parse(JsonOps.INSTANCE, json).getOrThrow().repeat());
	}
}
