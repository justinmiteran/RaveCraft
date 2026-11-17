package com.ravecraft.mod.core.audio.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class MonoFramerTest {
	@Test
	void framerSplitsAndPads() {
		MonoFramer framer = new MonoFramer();
		short[] samples = new short[2_000];
		for (int i = 0; i < samples.length; i++) {
			samples[i] = (short) (i + 1);
		}
		framer.push(samples, samples.length);
		short[] first = framer.poll();
		short[] second = framer.poll();
		assertNotNull(first);
		assertNotNull(second);
		assertEquals(1, first[0]);
		assertEquals(961, second[0]);
		assertNull(framer.poll());
		short[] rest = framer.flush();
		assertNotNull(rest);
		assertEquals(960, rest.length);
		assertEquals(1921, rest[0]);
		assertEquals(2000, rest[79]);
		for (int i = 80; i < 960; i++) {
			assertEquals(0, rest[i]);
		}
		assertNull(framer.flush());
	}

	@Test
	void framerKeepsPartialFramesAcrossPushes() {
		MonoFramer framer = new MonoFramer();
		framer.push(new short[500], 500);
		assertNull(framer.poll());
		framer.push(new short[500], 500);
		assertNotNull(framer.poll());
		assertNull(framer.poll());
	}
}
