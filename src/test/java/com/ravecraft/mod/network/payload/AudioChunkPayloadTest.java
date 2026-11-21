package com.ravecraft.mod.network.payload;

import com.ravecraft.mod.core.audio.stream.StreamFormat;

import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assertions.assertFalse;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.VarInt;
import org.junit.jupiter.api.Test;

class AudioChunkPayloadTest {
	private static List<byte[]> frames(int count, int size) {
		List<byte[]> frames = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			byte[] frame = new byte[size];
			frame[0] = (byte) i;
			frames.add(frame);
		}
		return frames;
	}

	@Test
	void roundTripsFiveFrames() {
		ByteBuf buf = Unpooled.buffer();
		AudioChunkPayload payload = new AudioChunkPayload(7, 42, frames(5, 380));
		AudioChunkPayload.CODEC.encode(buf, payload);
		AudioChunkPayload decoded = AudioChunkPayload.CODEC.decode(buf);
		assertEquals(7, decoded.sessionId());
		assertEquals(42, decoded.seq());
		assertEquals(5, decoded.frames().size());
		assertArrayEquals(payload.frames().get(4), decoded.frames().get(4));
	}

	@Test
	void relayedChunkRoundTripsSpeakerId() {
		ByteBuf buf = Unpooled.buffer();
		RelayedChunkPayload payload = new RelayedChunkPayload(3, UUID.randomUUID(), 9, frames(2, 10));
		RelayedChunkPayload.CODEC.encode(buf, payload);
		RelayedChunkPayload decoded = RelayedChunkPayload.CODEC.decode(buf);
		assertEquals(payload.speakerId(), decoded.speakerId());
		assertEquals(9, decoded.seq());
	}

	@Test
	void encodingSixFramesFails() {
		assertThrows(RuntimeException.class,
				() -> AudioChunkPayload.CODEC.encode(Unpooled.buffer(), new AudioChunkPayload(1, 0, frames(6, 10))));
	}

	@Test
	void decodingOversizedFrameFails() {
		ByteBuf buf = Unpooled.buffer();
		VarInt.write(buf, 1); // session
		VarInt.write(buf, 0); // seq
		VarInt.write(buf, 1); // one frame
		FriendlyByteBuf.writeByteArray(buf, new byte[381]);
		assertThrows(RuntimeException.class, () -> AudioChunkPayload.CODEC.decode(buf));
	}

	@Test
	void chunkWithoutAudioIsMalformed() {
		assertFalse(new AudioChunkPayload(1, 0, List.of()).isWellFormed());
		assertFalse(new AudioChunkPayload(1, 0, List.of(new byte[0], new byte[3])).isWellFormed());
		assertTrue(new AudioChunkPayload(1, 0, frames(5, 3)).isWellFormed());
	}

	@Test
	void costChargesPerChunkOverhead() {
		assertEquals(15 + StreamFormat.CHUNK_OVERHEAD_BYTES, new AudioChunkPayload(1, 0, frames(5, 3)).cost());
	}
}
