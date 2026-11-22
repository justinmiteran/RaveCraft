package com.ravecraft.mod.network.payload;

import com.ravecraft.mod.RaveCraft;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S→C: a chunk relayed to a listener, naming its speaker; the listener was told the speaker's host
 * ({@link SpeakerHostPayload}) before its first chunk.
 */
public record RelayedChunkPayload(int sessionId, UUID speakerId, int seq, List<byte[]> frames) implements CustomPacketPayload {
	public static final Type<RelayedChunkPayload> TYPE = new Type<>(RaveCraft.id("relayed_chunk"));
	public static final StreamCodec<ByteBuf, RelayedChunkPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, RelayedChunkPayload::sessionId,
			UUIDUtil.STREAM_CODEC, RelayedChunkPayload::speakerId,
			ByteBufCodecs.VAR_INT, RelayedChunkPayload::seq,
			Frames.CODEC, RelayedChunkPayload::frames,
			RelayedChunkPayload::new);

	@Override
	public Type<RelayedChunkPayload> type() {
		return TYPE;
	}
}
