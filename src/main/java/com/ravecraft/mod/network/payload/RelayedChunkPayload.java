package com.ravecraft.mod.network.payload;

import com.ravecraft.mod.RaveCraft;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** S→C: a chunk relayed to a listener, with the speaker position so the listener can play it without prior notice. */
public record RelayedChunkPayload(int sessionId, BlockPos pos, int seq, List<byte[]> frames) implements CustomPacketPayload {
	public static final Type<RelayedChunkPayload> TYPE = new Type<>(RaveCraft.id("relayed_chunk"));
	public static final StreamCodec<ByteBuf, RelayedChunkPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, RelayedChunkPayload::sessionId,
			BlockPos.STREAM_CODEC, RelayedChunkPayload::pos,
			ByteBufCodecs.VAR_INT, RelayedChunkPayload::seq,
			Frames.CODEC, RelayedChunkPayload::frames,
			RelayedChunkPayload::new);

	@Override
	public Type<RelayedChunkPayload> type() {
		return TYPE;
	}
}
