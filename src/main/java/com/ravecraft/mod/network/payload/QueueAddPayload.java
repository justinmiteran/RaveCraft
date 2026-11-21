package com.ravecraft.mod.network.payload;

import com.ravecraft.mod.RaveCraft;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C→S: append tracks to the queue of the speaker at {@code pos}. Only ids and titles: never a file path. */
public record QueueAddPayload(BlockPos pos, List<NewEntry> entries) implements CustomPacketPayload {
	public static final int MAX_ENTRIES = 50;
	public static final int MAX_TITLE = 128;

	/** A track to add: its id and its title. */
	public record NewEntry(UUID entryId, String title) {
		public static final StreamCodec<ByteBuf, NewEntry> CODEC = StreamCodec.composite(
				UUIDUtil.STREAM_CODEC, NewEntry::entryId,
				ByteBufCodecs.stringUtf8(MAX_TITLE), NewEntry::title,
				NewEntry::new);
	}

	public static final Type<QueueAddPayload> TYPE = new Type<>(RaveCraft.id("queue_add"));
	public static final StreamCodec<ByteBuf, QueueAddPayload> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, QueueAddPayload::pos,
			NewEntry.CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)), QueueAddPayload::entries,
			QueueAddPayload::new);

	/** Copies {@code entries}. */
	public QueueAddPayload {
		entries = List.copyOf(entries);
	}

	@Override
	public Type<QueueAddPayload> type() {
		return TYPE;
	}
}
