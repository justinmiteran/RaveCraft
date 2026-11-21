package com.ravecraft.mod.core.speaker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class SpeakerKeyTargetTest {
	private static OptionalInt choose(int selected, Integer... speakers) {
		Set<Integer> set = Set.of(speakers);
		return SpeakerKeyTarget.choose(selected, set::contains);
	}

	@Test
	void mainHandFirst() {
		assertEquals(3, choose(3, 3, 40, 0).getAsInt());
	}

	@Test
	void offhandBeforeHotbar() {
		assertEquals(40, choose(2, 40, 0).getAsInt());
	}

	@Test
	void hotbarInOrder() {
		assertEquals(5, choose(2, 7, 5).getAsInt());
	}

	@Test
	void hotbarBeforeInventory() {
		assertEquals(8, choose(2, 30, 8).getAsInt());
	}

	@Test
	void inventoryInOrder() {
		assertEquals(12, choose(2, 35, 12).getAsInt());
	}

	@Test
	void armorSlotsIgnored() {
		assertTrue(choose(2, 38).isEmpty());
	}

	@Test
	void noSpeaker() {
		assertTrue(choose(2).isEmpty());
	}
}
