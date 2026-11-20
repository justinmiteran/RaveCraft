package com.ravecraft.mod.core.acoustics.debug.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LossColorsTest {
	@Test
	void stops() {
		assertEquals(0xFF5DCAA5, LossColors.forLoss(0));
		assertEquals(0xFFEF9F27, LossColors.forLoss(10));
		assertEquals(0xFFE24B4A, LossColors.forLoss(30));
		assertEquals(0xFFE24B4A, LossColors.forLoss(45));
		assertEquals(0xFFE24B4A, LossColors.forLoss(Double.POSITIVE_INFINITY));
		assertEquals(0xFFE24B4A, LossColors.forLoss(Double.NaN));
		assertEquals(0xFF5DCAA5, LossColors.forLoss(-1));
	}

	@Test
	void interpolates() {
		int c = LossColors.forLoss(5);
		assertEquals(0xFF, c >>> 24);
		assertEquals(Math.round((0x5D + 0xEF) / 2.0), (c >> 16) & 0xFF);
		assertEquals(Math.round((0xCA + 0x9F) / 2.0), (c >> 8) & 0xFF);
		assertEquals(Math.round((0xA5 + 0x27) / 2.0), c & 0xFF);
	}

	@Test
	void absorption() {
		assertEquals(LossColors.forLoss(0), LossColors.forAbsorption(0));
		assertEquals(LossColors.forLoss(30), LossColors.forAbsorption(1));
		assertEquals(LossColors.forLoss(15), LossColors.forAbsorption(0.5));
		assertEquals(LossColors.forLoss(30), LossColors.forAbsorption(2));
	}

	@Test
	void alpha() {
		assertEquals(0x66123456, LossColors.withAlpha(0xFF123456, 0.4f));
		assertEquals(0xFF123456, LossColors.withAlpha(0x00123456, 1f));
	}
}
