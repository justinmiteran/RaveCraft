package com.ravecraft.mod.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.ravecraft.mod.core.speaker.SpeakerSettings;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Data carried by a portable speaker item: a stable identity and its settings.
 * Settings are flattened in the codecs as {@code powered} and {@code volume}.
 */
public record SpeakerData(UUID id, SpeakerSettings settings) {
	public static final Codec<SpeakerData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(SpeakerData::id),
			Codec.BOOL.fieldOf("powered").forGetter(data -> data.settings().powered()),
			Codec.INT.fieldOf("volume").forGetter(data -> data.settings().volume())
	).apply(instance, (id, powered, volume) -> new SpeakerData(id, new SpeakerSettings(powered, volume))));

	public static final StreamCodec<ByteBuf, SpeakerData> STREAM_CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, SpeakerData::id,
			ByteBufCodecs.BOOL, data -> data.settings().powered(),
			ByteBufCodecs.VAR_INT, data -> data.settings().volume(),
			(id, powered, volume) -> new SpeakerData(id, new SpeakerSettings(powered, volume))
	);

	/** Creates data with a fresh random id and the default settings. */
	public static SpeakerData create() {
		return new SpeakerData(UUID.randomUUID(), SpeakerSettings.DEFAULT);
	}

	public SpeakerData withSettings(SpeakerSettings settings) {
		return new SpeakerData(id, settings);
	}
}
