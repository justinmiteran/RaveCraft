package com.ravecraft.mod.network.payload;

import com.ravecraft.mod.RaveCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C→S: the player wants to stream into the speaker at {@code pos}; {@code request} is echoed in the answer. */
public record StreamOpenPayload(BlockPos pos, int request) implements CustomPacketPayload {
	public static final Type<StreamOpenPayload> TYPE = new Type<>(RaveCraft.id("stream_open"));
	public static final StreamCodec<ByteBuf, StreamOpenPayload> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, StreamOpenPayload::pos,
			ByteBufCodecs.VAR_INT, StreamOpenPayload::request,
			StreamOpenPayload::new);

	@Override
	public Type<StreamOpenPayload> type() {
		return TYPE;
	}
}
