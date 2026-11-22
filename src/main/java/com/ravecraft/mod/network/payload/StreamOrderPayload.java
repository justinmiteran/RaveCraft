package com.ravecraft.mod.network.payload;

import com.ravecraft.mod.RaveCraft;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S→C, to the owner of queue entry {@code entryId}: stream it into session {@code sessionId} from {@code positionMs}
 * ({@code start}), or stop that session. The server alone opens sessions. {@code speakerId} names the speaker, whose
 * host ({@link SpeakerHostPayload}) tells the owner where it hears its own stream from.
 */
public record StreamOrderPayload(int sessionId, UUID speakerId, boolean start, UUID entryId, long positionMs)
		implements CustomPacketPayload {
	public static final Type<StreamOrderPayload> TYPE = new Type<>(RaveCraft.id("stream_order"));
	public static final StreamCodec<ByteBuf, StreamOrderPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, StreamOrderPayload::sessionId,
			UUIDUtil.STREAM_CODEC, StreamOrderPayload::speakerId,
			ByteBufCodecs.BOOL, StreamOrderPayload::start,
			UUIDUtil.STREAM_CODEC, StreamOrderPayload::entryId,
			ByteBufCodecs.VAR_LONG, StreamOrderPayload::positionMs,
			StreamOrderPayload::new);

	@Override
	public Type<StreamOrderPayload> type() {
		return TYPE;
	}
}
