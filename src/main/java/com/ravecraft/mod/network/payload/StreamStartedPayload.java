package com.ravecraft.mod.network.payload;

import com.ravecraft.mod.RaveCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C→S: the owner's client opened the track of session {@code sessionId}, which lasts {@code durationMs}. */
public record StreamStartedPayload(int sessionId, long durationMs) implements CustomPacketPayload {
	public static final Type<StreamStartedPayload> TYPE = new Type<>(RaveCraft.id("stream_started"));
	public static final StreamCodec<ByteBuf, StreamStartedPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, StreamStartedPayload::sessionId,
			ByteBufCodecs.VAR_LONG, StreamStartedPayload::durationMs,
			StreamStartedPayload::new);

	@Override
	public Type<StreamStartedPayload> type() {
		return TYPE;
	}
}
