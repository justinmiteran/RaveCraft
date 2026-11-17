package com.ravecraft.mod.network.payload;

import com.ravecraft.mod.RaveCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S→C: session {@code sessionId} is over. With {@code drain}, listeners play what they have buffered first (the
 * track ended normally); without it they stop at once (stop, speaker broken or off, owner gone).
 */
public record SessionEndPayload(int sessionId, boolean drain) implements CustomPacketPayload {
	public static final Type<SessionEndPayload> TYPE = new Type<>(RaveCraft.id("session_end"));
	public static final StreamCodec<ByteBuf, SessionEndPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SessionEndPayload::sessionId,
			ByteBufCodecs.BOOL, SessionEndPayload::drain,
			SessionEndPayload::new);

	@Override
	public Type<SessionEndPayload> type() {
		return TYPE;
	}
}
