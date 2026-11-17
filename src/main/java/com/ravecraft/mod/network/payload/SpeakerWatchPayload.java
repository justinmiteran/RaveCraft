package com.ravecraft.mod.network.payload;

import com.ravecraft.mod.RaveCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C→S: the player opened ({@code open}) or closed the screen of the speaker at {@code pos}. */
public record SpeakerWatchPayload(BlockPos pos, boolean open) implements CustomPacketPayload {
	public static final Type<SpeakerWatchPayload> TYPE = new Type<>(RaveCraft.id("speaker_watch"));
	public static final StreamCodec<ByteBuf, SpeakerWatchPayload> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, SpeakerWatchPayload::pos,
			ByteBufCodecs.BOOL, SpeakerWatchPayload::open,
			SpeakerWatchPayload::new);

	@Override
	public Type<SpeakerWatchPayload> type() {
		return TYPE;
	}
}
