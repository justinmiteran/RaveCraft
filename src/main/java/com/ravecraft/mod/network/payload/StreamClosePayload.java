package com.ravecraft.mod.network.payload;

import com.ravecraft.mod.RaveCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C→S: the streaming player ended session {@code sessionId} (end of track or stop). */
public record StreamClosePayload(int sessionId) implements CustomPacketPayload {
	public static final Type<StreamClosePayload> TYPE = new Type<>(RaveCraft.id("stream_close"));
	public static final StreamCodec<ByteBuf, StreamClosePayload> CODEC =
			ByteBufCodecs.VAR_INT.map(StreamClosePayload::new, StreamClosePayload::sessionId);

	@Override
	public Type<StreamClosePayload> type() {
		return TYPE;
	}
}
