package com.ravecraft.mod.network.payload;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
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
	void relayedChunkRoundTripsPosition() {
		ByteBuf buf = Unpooled.buffer();
		RelayedChunkPayload payload = new RelayedChunkPayload(3, new BlockPos(10, -5, 300), 9, frames(2, 10));
		RelayedChunkPayload.CODEC.encode(buf, payload);
		RelayedChunkPayload decoded = RelayedChunkPayload.CODEC.decode(buf);
		assertEquals(payload.pos(), decoded.pos());
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
}
