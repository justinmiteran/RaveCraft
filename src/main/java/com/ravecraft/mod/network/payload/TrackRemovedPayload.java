package com.ravecraft.mod.network.payload;

import com.ravecraft.mod.RaveCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** S→C, to the owner only: their track {@code title} was removed from a queue because it could not be read. */
public record TrackRemovedPayload(String title) implements CustomPacketPayload {
	public static final Type<TrackRemovedPayload> TYPE = new Type<>(RaveCraft.id("track_removed"));
	public static final StreamCodec<ByteBuf, TrackRemovedPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(128), TrackRemovedPayload::title,
			TrackRemovedPayload::new);

	@Override
	public Type<TrackRemovedPayload> type() {
		return TYPE;
	}
}
