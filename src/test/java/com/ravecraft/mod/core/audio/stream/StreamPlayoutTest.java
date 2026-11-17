package com.ravecraft.mod.core.audio.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ravecraft.mod.core.audio.Pcm16;
import com.ravecraft.mod.core.audio.opus.OpusMonoEncoder;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StreamPlayoutTest {
	private static double rms(byte[] pcm) {
		short[] samples = Pcm16.toShorts(pcm, pcm.length);
		double sum = 0;
		for (short s : samples) {
			sum += (double) s * s;
		}
		return Math.sqrt(sum / samples.length);
	}

	@Test
	void playoutAlwaysReturnsFullBlock() {
		byte[] block = new StreamPlayout(0).nextBlock();
		assertEquals(9_600, block.length);
		for (byte b : block) {
			assertEquals(0, b);
		}
	}

	@Test
	void playsEncodedToneAfterPrebuffer() {
		StreamPlayout playout = new StreamPlayout(0);
		OpusMonoEncoder encoder = new OpusMonoEncoder();
		int frame = 0;
		for (int seq = 0; seq < 12; seq++) {
			List<byte[]> frames = new ArrayList<>();
			for (int i = 0; i < StreamFormat.FRAMES_PER_CHUNK; i++) {
				short[] pcm = new short[StreamFormat.FRAME_SAMPLES];
				for (int s = 0; s < pcm.length; s++) {
					long n = (long) frame * StreamFormat.FRAME_SAMPLES + s;
					pcm[s] = (short) Math.round(10_000 * Math.sin(2 * Math.PI * 1000 * n / StreamFormat.SAMPLE_RATE));
				}
				frames.add(encoder.encode(pcm));
				frame++;
			}
			playout.offer(seq, frames, 0);
		}
		playout.nextBlock(); // first block includes the codec warm-up
		for (int i = 0; i < 5; i++) {
			assertTrue(rms(playout.nextBlock()) > 3_000);
		}
	}

	@Test
	void idleDetection() {
		StreamPlayout playout = new StreamPlayout(1_000);
		assertFalse(playout.idleFor(5_000, 5_999));
		assertTrue(playout.idleFor(5_000, 6_000));
		playout.offer(0, List.of(new byte[]{1}), 7_000);
		assertFalse(playout.idleFor(5_000, 11_999));
		assertTrue(playout.idleFor(5_000, 12_000));
	}
}
