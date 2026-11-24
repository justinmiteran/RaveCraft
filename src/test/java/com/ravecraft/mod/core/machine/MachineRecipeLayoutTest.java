package com.ravecraft.mod.core.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ravecraft.mod.core.machine.MachineRecipeLayout.Pos;
import org.junit.jupiter.api.Test;

class MachineRecipeLayoutTest {
	@Test
	void oneInputMatchesTheCoilWinderPage() {
		MachineRecipeLayout page = new MachineRecipeLayout(1, false);
		assertEquals(99, page.width());
		assertEquals(new Pos(1, 1), page.input(0));
		assertEquals(new Pos(1, 37), page.energy());
		assertEquals(new Pos(78, 19), page.output());
		assertEquals(new Pos(2, 20), page.bolt());
		assertEquals(new Pos(42, 18), page.arrow());
	}

	@Test
	void threeInputsMatchTheEtcherPage() {
		MachineRecipeLayout page = new MachineRecipeLayout(3, false);
		assertEquals(117, page.width());
		assertEquals(new Pos(1, 1), page.input(0));
		assertEquals(new Pos(19, 1), page.input(1));
		assertEquals(new Pos(37, 1), page.input(2));
		assertEquals(new Pos(19, 37), page.energy());
		assertEquals(new Pos(96, 19), page.output());
		assertEquals(new Pos(60, 18), page.arrow());
	}

	@Test
	void moldComesFirstOnTheExtruderPage() {
		MachineRecipeLayout page = new MachineRecipeLayout(1, true);
		assertEquals(121, page.width());
		assertEquals(new Pos(1, 1), page.moldSlot());
		assertEquals(new Pos(23, 1), page.input(0));
		assertEquals(new Pos(100, 19), page.output());
		assertEquals(new Pos(64, 18), page.arrow());
	}

	@Test
	void secondsHaveNoTrailingZero() {
		assertEquals("10", MachineRecipeLayout.seconds(200));
		assertEquals("1.5", MachineRecipeLayout.seconds(30));
		assertEquals("0.05", MachineRecipeLayout.seconds(1));
	}
}
