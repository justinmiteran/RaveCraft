package com.ravecraft.mod.network.payload;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.core.queue.QueueEntry;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S→C, once per session and player: what session {@code sessionId} of the speaker at {@code pos} plays, and who
 * added it. Sent to the streaming owner when it starts and to each listener before its first relayed chunk.
 */
public record NowPlayingPayload(int sessionId, BlockPos pos, UUID entryId, String title, String ownerName)
		implements CustomPacketPayload {
	public static final Type<NowPlayingPayload> TYPE = new Type<>(RaveCraft.id("now_playing"));
	public static final StreamCodec<ByteBuf, NowPlayingPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, NowPlayingPayload::sessionId,
			BlockPos.STREAM_CODEC, NowPlayingPayload::pos,
			UUIDUtil.STREAM_CODEC, NowPlayingPayload::entryId,
			ByteBufCodecs.stringUtf8(QueueEntry.MAX_TITLE), NowPlayingPayload::title,
			ByteBufCodecs.stringUtf8(QueueEntry.MAX_OWNER_NAME), NowPlayingPayload::ownerName,
			NowPlayingPayload::new);

	/** The notice of {@code entry}, whose title and owner name {@link QueueEntry} already capped. */
	public static NowPlayingPayload of(int sessionId, BlockPos pos, QueueEntry entry) {
		return new NowPlayingPayload(sessionId, pos.immutable(), entry.entryId(), entry.title(), entry.ownerName());
	}

	@Override
	public Type<NowPlayingPayload> type() {
		return TYPE;
	}
}
