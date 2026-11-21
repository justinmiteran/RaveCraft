package com.ravecraft.mod.network.payload;

import com.ravecraft.mod.RaveCraft;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** S→C: the player no longer watches speaker {@code speakerId} (gone, or out of reach): its screen closes. */
public record SpeakerClosedPayload(UUID speakerId) implements CustomPacketPayload {
	public static final Type<SpeakerClosedPayload> TYPE = new Type<>(RaveCraft.id("speaker_closed"));
	public static final StreamCodec<ByteBuf, SpeakerClosedPayload> CODEC =
			UUIDUtil.STREAM_CODEC.map(SpeakerClosedPayload::new, SpeakerClosedPayload::speakerId);

	@Override
	public Type<SpeakerClosedPayload> type() {
		return TYPE;
	}
}
