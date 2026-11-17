package com.ravecraft.mod.network.payload;

import com.ravecraft.mod.RaveCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** S→C, to the streaming player: start sending session {@code sessionId}, or stop it. */
public record StreamOrderPayload(int sessionId, BlockPos pos, boolean start) implements CustomPacketPayload {
	public static final Type<StreamOrderPayload> TYPE = new Type<>(RaveCraft.id("stream_order"));
	public static final StreamCodec<ByteBuf, StreamOrderPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, StreamOrderPayload::sessionId,
			BlockPos.STREAM_CODEC, StreamOrderPayload::pos,
			ByteBufCodecs.BOOL, StreamOrderPayload::start,
			StreamOrderPayload::new);

	@Override
	public Type<StreamOrderPayload> type() {
		return TYPE;
	}
}
