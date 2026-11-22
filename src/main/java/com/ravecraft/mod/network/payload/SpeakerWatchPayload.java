package com.ravecraft.mod.network.payload;

import com.ravecraft.mod.RaveCraft;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C→S: the player opened ({@code open}) or closed the screen of speaker {@code speakerId}. */
public record SpeakerWatchPayload(UUID speakerId, boolean open) implements CustomPacketPayload {
	public static final Type<SpeakerWatchPayload> TYPE = new Type<>(RaveCraft.id("speaker_watch"));
	public static final StreamCodec<ByteBuf, SpeakerWatchPayload> CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, SpeakerWatchPayload::speakerId,
			ByteBufCodecs.BOOL, SpeakerWatchPayload::open,
			SpeakerWatchPayload::new);

	@Override
	public Type<SpeakerWatchPayload> type() {
		return TYPE;
	}
}
