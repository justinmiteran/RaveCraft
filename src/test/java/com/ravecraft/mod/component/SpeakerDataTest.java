package com.ravecraft.mod.component;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.ravecraft.mod.core.queue.QueueEntry;
import com.ravecraft.mod.core.queue.SpeakerQueue;
import com.ravecraft.mod.core.speaker.SpeakerSettings;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
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
}
