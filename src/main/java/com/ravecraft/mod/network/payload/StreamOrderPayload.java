package com.ravecraft.mod.network.payload;

import com.ravecraft.mod.RaveCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S→C, to the streaming player: start sending session {@code sessionId}, or stop it. Answers to a
 * {@link StreamOpenPayload} carry its {@code request}: a grant (start), or a refusal (stop, {@link #NO_SESSION}).
 * Stops the player did not ask for carry {@link #NO_REQUEST}.
 */
public record StreamOrderPayload(int sessionId, BlockPos pos, boolean start, int request) implements CustomPacketPayload {
	public static final int NO_SESSION = -1;
	public static final int NO_REQUEST = -1;
	public static final Type<StreamOrderPayload> TYPE = new Type<>(RaveCraft.id("stream_order"));
	public static final StreamCodec<ByteBuf, StreamOrderPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, StreamOrderPayload::sessionId,
			BlockPos.STREAM_CODEC, StreamOrderPayload::pos,
			ByteBufCodecs.BOOL, StreamOrderPayload::start,
			ByteBufCodecs.VAR_INT, StreamOrderPayload::request,
			StreamOrderPayload::new);

	@Override
	public Type<StreamOrderPayload> type() {
		return TYPE;
	}
}
