package com.ravecraft.mod.network.payload;

import com.ravecraft.mod.RaveCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C→S: the streaming player ended session {@code sessionId}. {@code finished} is true at the natural end of a track
 * (listeners play their buffered tail) and false on a stop (listeners cut at once).
 */
public record StreamClosePayload(int sessionId, boolean finished) implements CustomPacketPayload {
	public static final Type<StreamClosePayload> TYPE = new Type<>(RaveCraft.id("stream_close"));
	public static final StreamCodec<ByteBuf, StreamClosePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, StreamClosePayload::sessionId,
			ByteBufCodecs.BOOL, StreamClosePayload::finished,
			StreamClosePayload::new);

	@Override
	public Type<StreamClosePayload> type() {
		return TYPE;
	}
}
