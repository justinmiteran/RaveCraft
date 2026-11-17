package com.ravecraft.mod.network.payload;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.core.audio.stream.StreamFormat;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C→S: about 100 ms of Opus audio for session {@code sessionId}; chunk {@code seq} counts from 0. */
public record AudioChunkPayload(int sessionId, int seq, List<byte[]> frames) implements CustomPacketPayload {
	public static final Type<AudioChunkPayload> TYPE = new Type<>(RaveCraft.id("audio_chunk"));
	public static final StreamCodec<ByteBuf, AudioChunkPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, AudioChunkPayload::sessionId,
			ByteBufCodecs.VAR_INT, AudioChunkPayload::seq,
			Frames.CODEC, AudioChunkPayload::frames,
			AudioChunkPayload::new);

	public int byteSize() {
		int total = 0;
		for (byte[] frame : frames) {
			total += frame.length;
		}
		return total;
	}

	/** At least one frame, and no empty frame: anything else carries no audio and is not relayed. */
	public boolean isWellFormed() {
		if (frames.isEmpty()) {
			return false;
		}
		for (byte[] frame : frames) {
			if (frame.length == 0) {
				return false;
			}
		}
		return true;
	}

	/** What the chunk costs against the sender's rate limit. */
	public int cost() {
		return byteSize() + StreamFormat.CHUNK_OVERHEAD_BYTES;
	}

	@Override
	public Type<AudioChunkPayload> type() {
		return TYPE;
	}
}
