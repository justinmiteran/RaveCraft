package com.ravecraft.mod.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.ravecraft.mod.core.queue.SpeakerQueue;
import com.ravecraft.mod.core.speaker.SpeakerSettings;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Data carried by a portable speaker, placed or as an item: a stable identity, its settings and its shared queue.
 * Settings are flattened in the codecs as {@code powered} and {@code volume}.
 */
public record SpeakerData(UUID id, SpeakerSettings settings, SpeakerQueue queue) {
	// New fields must use optionalFieldOf with a default so existing worlds still decode.
	public static final Codec<SpeakerData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(SpeakerData::id),
			Codec.BOOL.fieldOf("powered").forGetter(data -> data.settings().powered()),
			Codec.INT.fieldOf("volume").forGetter(data -> data.settings().volume()),
			QueueCodecs.QUEUE.optionalFieldOf("queue", SpeakerQueue.EMPTY).forGetter(SpeakerData::queue)
	).apply(instance, (id, powered, volume, queue) -> new SpeakerData(id, new SpeakerSettings(powered, volume), queue)));

	public static final StreamCodec<ByteBuf, SpeakerData> STREAM_CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, SpeakerData::id,
			ByteBufCodecs.BOOL, data -> data.settings().powered(),
			ByteBufCodecs.VAR_INT, data -> data.settings().volume(),
			QueueCodecs.QUEUE_STREAM, SpeakerData::queue,
			(id, powered, volume, queue) -> new SpeakerData(id, new SpeakerSettings(powered, volume), queue)
	);

	/** Data with an empty queue. */
	public SpeakerData(UUID id, SpeakerSettings settings) {
		this(id, settings, SpeakerQueue.EMPTY);
	}

	/** Creates data with a fresh random id, the default settings and an empty queue. */
	public static SpeakerData create() {
		return new SpeakerData(UUID.randomUUID(), SpeakerSettings.DEFAULT);
	}

	/** The same speaker with {@code settings}. */
	public SpeakerData withId(UUID id) {
		return new SpeakerData(id, settings, queue);
	}

	/** The same speaker with {@code settings}. */
	public SpeakerData withSettings(SpeakerSettings settings) {
		return new SpeakerData(id, settings, queue);
	}

	/** The same speaker with {@code queue}. */
	public SpeakerData withQueue(SpeakerQueue queue) {
		return new SpeakerData(id, settings, queue);
	}
}
