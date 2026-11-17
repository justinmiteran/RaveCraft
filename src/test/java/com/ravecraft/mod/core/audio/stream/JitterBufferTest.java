package com.ravecraft.mod.core.audio.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.ravecraft.mod.core.audio.stream.JitterBuffer.Audio;
import com.ravecraft.mod.core.audio.stream.JitterBuffer.Lost;
import com.ravecraft.mod.core.audio.stream.JitterBuffer.Slot;
import com.ravecraft.mod.core.audio.stream.JitterBuffer.Waiting;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class JitterBufferTest {
	private final JitterBuffer buffer = new JitterBuffer(10, 50);

	/** Frames {@code from..to} inclusive, each holding its own index as a single byte. */
	private void offer(int from, int to) {
		List<byte[]> frames = new ArrayList<>();
		for (int i = from; i <= to; i++) {
			frames.add(new byte[]{(byte) i});
		}
		buffer.offer(from, frames);
	}

	private void offerChunk(int seq) {
		offer(seq * 5, seq * 5 + 4);
	}

	private void assertAudio(int expected, Slot slot) {
		Audio audio = assertInstanceOf(Audio.class, slot);
		assertEquals((byte) expected, audio.frame()[0]);
	}

	@Test
	void prebuffersUntilTarget() {
		offerChunk(0);
		offerChunk(1);
		assertInstanceOf(Waiting.class, buffer.poll());
		offer(10, 10);
		assertAudio(0, buffer.poll());
		assertAudio(1, buffer.poll());
	}

	@Test
	void reordersOutOfOrderChunks() {
		offerChunk(2);
		offerChunk(0);
		offerChunk(1);
		for (int i = 0; i < 15; i++) {
			assertAudio(i, buffer.poll());
		}
	}

	@Test
	void dropsDuplicatesAndTooOld() {
		offerChunk(0);
		offerChunk(0);
		offerChunk(1);
		offerChunk(2);
		for (int i = 0; i < 5; i++) {
			assertAudio(i, buffer.poll());
		}
		offerChunk(0);
		assertEquals(10, buffer.bufferedFrames());
		for (int i = 5; i < 15; i++) {
			assertAudio(i, buffer.poll());
		}
		assertInstanceOf(Waiting.class, buffer.poll());
	}

	@Test
	void missingFrameIsLost() {
		offer(0, 4);
		offer(10, 14);
		for (int i = 0; i < 5; i++) {
			assertAudio(i, buffer.poll());
		}
		for (int i = 0; i < 5; i++) {
			assertInstanceOf(Lost.class, buffer.poll());
		}
		for (int i = 10; i < 15; i++) {
			assertAudio(i, buffer.poll());
		}
	}

	@Test
	void underrunReturnsToPrebufferingAndSkipsGap() {
		offer(0, 14);
		for (int i = 0; i < 15; i++) {
			assertAudio(i, buffer.poll());
		}
		assertInstanceOf(Waiting.class, buffer.poll());
		offer(100, 114);
		assertAudio(104, buffer.poll());
	}

	@Test
	void farAheadFrameResyncs() {
		offer(0, 14);
		for (int i = 0; i < 5; i++) {
			assertAudio(i, buffer.poll());
		}
		offer(500, 504);
		assertEquals(5, buffer.bufferedFrames());
		assertInstanceOf(Waiting.class, buffer.poll());
		offer(505, 514);
		assertAudio(500, buffer.poll());
	}

	@Test
	void memoryIsCapped() {
		for (int seq = 0; seq < 40; seq++) {
			offerChunk(seq);
		}
		assertEquals(60, buffer.bufferedFrames());
	}

	@Test
	void drainPlaysTailBelowTargetThenWaits() {
		offer(0, 3);
		assertInstanceOf(Waiting.class, buffer.poll());
		buffer.drain();
		for (int i = 0; i < 4; i++) {
			assertAudio(i, buffer.poll());
		}
		assertInstanceOf(Waiting.class, buffer.poll());
		assertEquals(0, buffer.bufferedFrames());
	}

	@Test
	void stalledBacklogResumesFromNewestFrames() {
		offer(0, 10);
		for (int i = 0; i <= 10; i++) {
			assertAudio(i, buffer.poll());
		}
		assertInstanceOf(Waiting.class, buffer.poll());
		offer(11, 40);
		assertAudio(30, buffer.poll());
		assertEquals(10, buffer.bufferedFrames());
	}
}
