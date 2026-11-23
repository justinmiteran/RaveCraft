package com.ravecraft.mod.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class OreVeinTest {
	@Test
	void triangleIsCentredBetweenItsBounds() {
		OreVein vein = new OreVein(6, 3, -64, -16, true);
		assertEquals(-40, vein.middleY());
		assertEquals(24, vein.halfRange());
	}

	@Test
	void neodymiumMatchesTheSpec() {
		assertEquals(new OreVein(6, 3, -64, -16, true), OreVeins.NEODYMIUM);
		assertEquals(new OreVein(17, 12, 20, 80, false), OreVeins.OIL_SHALE);
		assertEquals(8, OreVeins.OIL_SHALE_DRY_COUNT);
		assertEquals(new OreVein(64, 2, 0, 80, false), OreVeins.SHALE);
	}

	@Test
	void boundsMustBeOrdered() {
		assertThrows(IllegalArgumentException.class, () -> new OreVein(6, 3, 10, 0, false));
		assertThrows(IllegalArgumentException.class, () -> new OreVein(0, 3, 0, 10, false));
	}
}
