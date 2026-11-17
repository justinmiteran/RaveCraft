package com.ravecraft.mod.core.audio;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

class Pcm16Test {
	private static byte[] samples(int... values) {
		ByteBuffer buffer = ByteBuffer.allocate(values.length * 2).order(ByteOrder.LITTLE_ENDIAN);
		for (int value : values) {
			buffer.putShort((short) value);
		}
		return buffer.array();
	}

	@Test
	void stereoFramesAreAveraged() {
		byte[] stereo = samples(1000, 3000, -32768, -32768);
		assertArrayEquals(samples(2000, -32768), Pcm16.downmixToMono(stereo, stereo.length, 2));
	}

	@Test
	void monoIsCopiedUnchanged() {
		byte[] mono = samples(12, -7, 32767);
		assertArrayEquals(mono, Pcm16.downmixToMono(mono, mono.length, 1));
	}

	@Test
	void bytesPastLengthAreIgnored() {
		byte[] stereo = samples(100, 300, 5000, 5000);
		assertArrayEquals(samples(200), Pcm16.downmixToMono(stereo, 4, 2));
	}

	@Test
	void toShortsLittleEndian() {
		assertArrayEquals(new short[]{0x0201, -1}, Pcm16.toShorts(new byte[]{0x01, 0x02, (byte) 0xFF, (byte) 0xFF, 0x7F}, 5));
	}
}
