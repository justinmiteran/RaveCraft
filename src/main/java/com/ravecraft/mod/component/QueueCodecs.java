package com.ravecraft.mod.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.ravecraft.mod.core.queue.QueueEntry;
import com.ravecraft.mod.core.queue.SpeakerQueue;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Codecs for the shared queue persisted in {@link SpeakerData}. Over-long data is truncated, never rejected. */
public final class QueueCodecs {
	public static final Codec<QueueEntry> ENTRY = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(QueueEntry::entryId),
			UUIDUtil.CODEC.fieldOf("owner").forGetter(QueueEntry::owner),
			Codec.STRING.fieldOf("owner_name").forGetter(QueueEntry::ownerName),
			Codec.STRING.fieldOf("title").forGetter(QueueEntry::title)
	).apply(instance, QueueEntry::new));

	public static final Codec<SpeakerQueue> QUEUE = RecordCodecBuilder.create(instance -> instance.group(
			ENTRY.listOf().optionalFieldOf("entries", List.of()).forGetter(SpeakerQueue::entries),
			UUIDUtil.CODEC.optionalFieldOf("current").forGetter(SpeakerQueue::current),
			Codec.LONG.optionalFieldOf("position_ms", 0L).forGetter(SpeakerQueue::positionMs)
	).apply(instance, SpeakerQueue::new));

	public static final StreamCodec<ByteBuf, QueueEntry> ENTRY_STREAM = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, QueueEntry::entryId,
			UUIDUtil.STREAM_CODEC, QueueEntry::owner,
			ByteBufCodecs.stringUtf8(QueueEntry.MAX_OWNER_NAME), QueueEntry::ownerName,
			ByteBufCodecs.stringUtf8(QueueEntry.MAX_TITLE), QueueEntry::title,
			QueueEntry::new);

	public static final StreamCodec<ByteBuf, SpeakerQueue> QUEUE_STREAM = StreamCodec.composite(
			ENTRY_STREAM.apply(ByteBufCodecs.list(SpeakerQueue.MAX_ENTRIES)), SpeakerQueue::entries,
			ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), SpeakerQueue::current,
			ByteBufCodecs.VAR_LONG, SpeakerQueue::positionMs,
			SpeakerQueue::new);

	private QueueCodecs() {
	}
}
