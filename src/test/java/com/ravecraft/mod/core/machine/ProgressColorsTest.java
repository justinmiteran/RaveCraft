package com.ravecraft.mod.core.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ProgressColorsTest {
	private static final int OPAQUE_COPPER = 0xFFC46A46;
	private static final int OPAQUE_WHITE = 0xFFFFFFFF;

	@Test
	void knownInputGetsItsColor() {
		ProgressColors colors = ProgressColors.parse(Map.of("minecraft:copper_ingot", "#C46A46"));
		assertEquals(OPAQUE_COPPER, colors.colorOf("minecraft:copper_ingot"));
	}

	@Test
	void unknownInputIsWhite() {
		ProgressColors colors = ProgressColors.parse(Map.of("minecraft:copper_ingot", "#C46A46"));
		assertEquals(OPAQUE_WHITE, colors.colorOf("minecraft:dirt"));
		assertEquals(OPAQUE_WHITE, ProgressColors.EMPTY.colorOf("minecraft:copper_ingot"));
	}

	@Test
	void hashIsOptionalAndCaseIgnored() {
		ProgressColors colors = ProgressColors.parse(Map.of("a", "c46a46", "b", "#c46A46"));
		assertEquals(OPAQUE_COPPER, colors.colorOf("a"));
		assertEquals(OPAQUE_COPPER, colors.colorOf("b"));
	}

	@Test
	void malformedColorsAreSkipped() {
		ProgressColors colors = ProgressColors.parse(
				Map.of("short", "#FFF", "letters", "#GGGGGG", "long", "#C46A4600", "ok", "#C46A46"));
		assertEquals(OPAQUE_WHITE, colors.colorOf("short"));
		assertEquals(OPAQUE_WHITE, colors.colorOf("letters"));
		assertEquals(OPAQUE_WHITE, colors.colorOf("long"));
		assertEquals(OPAQUE_COPPER, colors.colorOf("ok"));
		assertEquals(3, colors.skipped());
	}
}
