package com.ravecraft.mod.core.machine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ArrowShadesTest {
	private static int rgb(int r, int g, int b) {
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	@Test
	void copperMatchesTheValidatedArrow() {
		int[] expected = {rgb(110, 72, 36), rgb(168, 92, 44), rgb(210, 108, 67), rgb(211, 116, 82), rgb(213, 125, 97),
				rgb(214, 132, 109)};
		assertArrayEquals(expected, ArrowShades.layers(rgb(196, 106, 70)));
	}

	@Test
	void greyInputGivesCoolSteel() {
		int[] expected = {rgb(65, 70, 81), rgb(97, 102, 115), rgb(130, 135, 147), rgb(139, 143, 154),
				rgb(148, 152, 161), rgb(155, 158, 167)};
		assertArrayEquals(expected, ArrowShades.layers(rgb(216, 216, 216)));
	}

	@Test
	void neodymiumMatchesTheValidatedArrow() {
		int[] expected = {rgb(68, 54, 92), rgb(91, 74, 138), rgb(114, 101, 176), rgb(123, 112, 180),
				rgb(133, 124, 185), rgb(140, 133, 188)};
		assertArrayEquals(expected, ArrowShades.layers(rgb(167, 160, 200)));
	}

	@Test
	void layersGetLighterAndStayOpaque() {
		int[] layers = ArrowShades.layers(rgb(250, 205, 60));
		assertEquals(ArrowShades.LAYERS, layers.length);
		for (int i = 0; i < layers.length; i++) {
			assertEquals(0xFF, layers[i] >>> 24, "opaque layer " + i);
			if (i > 0) {
				assertTrue(sum(layers[i]) > sum(layers[i - 1]), "layer " + i + " lighter than " + (i - 1));
			}
		}
	}

	@Test
	void whiteGivesTheSameSteelAsAnyGrey() {
		int[] white = ArrowShades.layers(ProgressColors.WHITE);
		int[] grey = ArrowShades.layers(rgb(216, 216, 216));
		assertArrayEquals(grey, white);
	}

	private static int sum(int argb) {
		return (argb >> 16 & 0xFF) + (argb >> 8 & 0xFF) + (argb & 0xFF);
	}
}
