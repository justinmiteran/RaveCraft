package com.ravecraft.mod.network.payload;

import com.ravecraft.mod.core.audio.stream.StreamFormat;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Codec of the Opus frames of one chunk, bounded on both ends: encoding over the caps throws, decoding rejects. */
final class Frames {
	static final StreamCodec<ByteBuf, List<byte[]>> CODEC =
			ByteBufCodecs.byteArray(StreamFormat.MAX_FRAME_BYTES).apply(ByteBufCodecs.list(StreamFormat.FRAMES_PER_CHUNK));

	private Frames() {
	}
}
