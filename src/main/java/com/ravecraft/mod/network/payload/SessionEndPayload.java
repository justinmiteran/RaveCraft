package com.ravecraft.mod.network.payload;

import com.ravecraft.mod.RaveCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** S→C: session {@code sessionId} is over; listeners stop playing it. */
public record SessionEndPayload(int sessionId) implements CustomPacketPayload {
	public static final Type<SessionEndPayload> TYPE = new Type<>(RaveCraft.id("session_end"));
	public static final StreamCodec<ByteBuf, SessionEndPayload> CODEC =
			ByteBufCodecs.VAR_INT.map(SessionEndPayload::new, SessionEndPayload::sessionId);

	@Override
	public Type<SessionEndPayload> type() {
		return TYPE;
	}
}
