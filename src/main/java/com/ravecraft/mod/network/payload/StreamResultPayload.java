package com.ravecraft.mod.network.payload;

import com.ravecraft.mod.RaveCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C→S: the track ordered for session {@code sessionId} cannot be found or read. */
public record StreamResultPayload(int sessionId) implements CustomPacketPayload {
	public static final Type<StreamResultPayload> TYPE = new Type<>(RaveCraft.id("stream_result"));
	public static final StreamCodec<ByteBuf, StreamResultPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, StreamResultPayload::sessionId,
			StreamResultPayload::new);

	@Override
	public Type<StreamResultPayload> type() {
		return TYPE;
	}
}
