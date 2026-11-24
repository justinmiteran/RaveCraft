package com.ravecraft.mod.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class OreDropsTest {
	@Test
	void fortuneThreeAtMostQuadruplesTheDrop() {
		assertEquals(20, OreDrops.maxWithFortune(5), "like vanilla copper: 2 to 5, up to 20");
		assertEquals(4, OreDrops.maxWithFortune(OreDrops.RAW_NEODYMIUM));
		assertEquals(12, OreDrops.maxWithFortune(OreDrops.BITUMEN_MAX));
	}

	@Test
	void bitumenDropsOneToThree() {
		assertEquals(1, OreDrops.BITUMEN_MIN);
		assertEquals(3, OreDrops.BITUMEN_MAX);
	}
}
