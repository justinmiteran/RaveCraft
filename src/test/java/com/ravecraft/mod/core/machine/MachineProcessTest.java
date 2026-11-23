package com.ravecraft.mod.core.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ravecraft.mod.core.machine.MachineProcess.TickResult;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.Test;

class MachineProcessTest {
	private static final String WIRE = "wire";
	private static final String COIL = "coil";

	/** Counts refuels and hands out a fixed number of units each time. */
	private static final class Fuel implements IntSupplier {
		private final int units;
		private int calls;

		Fuel(int units) {
			this.units = units;
		}

		@Override
		public int getAsInt() {
			calls++;
			return units;
		}
	}

	private static MachineProcess running(String recipe) {
		MachineProcess process = new MachineProcess();
		process.setRecipe(recipe, MachineConstants.DEFAULT_PROCESSING_TICKS);
		return process;
	}

	private static int runOperation(MachineProcess process, IntSupplier fuel) {
		int ticks = 0;
		TickResult result;
		do {
			result = process.tick(true, fuel);
			ticks++;
		} while (result != TickResult.DONE && ticks < 1000);
		return ticks;
	}

	@Test
	void dustGivesEightOperations() {
		MachineProcess process = running(WIRE);
		Fuel dust = new Fuel(MachineConstants.OPERATIONS_PER_DUST);
		for (int i = 0; i < 8; i++) {
			runOperation(process, dust);
		}
		assertEquals(1, dust.calls);
		assertEquals(0, process.energy());
		process.tick(true, dust);
		assertEquals(2, dust.calls);
	}

	@Test
	void blockGivesSeventyTwo() {
		MachineProcess process = running(WIRE);
		Fuel block = new Fuel(MachineConstants.OPERATIONS_PER_BLOCK);
		process.tick(true, block);
		assertEquals(71, process.energy());
		assertEquals(72, MachineConstants.MAX_ENERGY);
	}

	@Test
	void operationTakesHundredTicksAndProducesOnce() {
		MachineProcess process = running(WIRE);
		Fuel dust = new Fuel(8);
		for (int i = 1; i < 100; i++) {
			assertEquals(TickResult.WORKING, process.tick(true, dust), "tick " + i);
		}
		assertEquals(TickResult.DONE, process.tick(true, dust));
		assertEquals(0, process.progress());
		assertEquals(TickResult.WORKING, process.tick(true, dust));
		assertEquals(1, process.progress());
	}

	@Test
	void fullOutputBlocksAndKeepsProgress() {
		MachineProcess process = running(WIRE);
		Fuel dust = new Fuel(8);
		for (int i = 1; i < 100; i++) {
			process.tick(false, dust);
		}
		assertEquals(TickResult.BLOCKED, process.tick(false, dust));
		assertEquals(TickResult.BLOCKED, process.tick(false, dust));
		assertEquals(100, process.progress());
		assertEquals(TickResult.DONE, process.tick(true, dust));
		assertEquals(7, process.energy());
	}

	@Test
	void noEnergyWaitsWithProgressKept() {
		MachineProcess process = running(WIRE);
		Fuel none = new Fuel(0);
		assertEquals(TickResult.WAITING_ENERGY, process.tick(true, none));
		assertEquals(TickResult.WAITING_ENERGY, process.tick(true, none));
		assertEquals(0, process.progress());
		assertFalse(process.running());
		assertEquals(TickResult.WORKING, process.tick(true, new Fuel(8)));
		assertTrue(process.running());
	}

	@Test
	void changedRecipeResetsProgress() {
		MachineProcess process = running(WIRE);
		Fuel dust = new Fuel(8);
		for (int i = 0; i < 50; i++) {
			process.tick(true, dust);
		}
		process.setRecipe(COIL, 100);
		assertEquals(0, process.progress());
		assertEquals(100, runOperation(process, dust));
	}

	@Test
	void sameRecipeKeepsProgress() {
		MachineProcess process = running(WIRE);
		Fuel dust = new Fuel(8);
		for (int i = 0; i < 50; i++) {
			process.tick(true, dust);
		}
		process.setRecipe(WIRE, 100);
		assertEquals(50, process.progress());
		assertEquals(50, runOperation(process, dust));
	}

	@Test
	void noRecipeIsIdle() {
		MachineProcess process = running(WIRE);
		Fuel dust = new Fuel(8);
		process.tick(true, dust);
		process.setRecipe(null, 0);
		assertEquals(0, process.progress());
		assertEquals(TickResult.IDLE, process.tick(true, dust));
		assertEquals(7, process.energy());
	}

	@Test
	void energyNeverExceedsMax() {
		MachineProcess process = new MachineProcess();
		process.load(500, 20, null);
		assertEquals(MachineConstants.MAX_ENERGY, process.energy());
		assertEquals(20, process.progress());
		process.load(-3, -1, null);
		assertEquals(0, process.energy());
		assertEquals(0, process.progress());
	}

	@Test
	void customDurationIsUsed() {
		MachineProcess process = new MachineProcess();
		process.setRecipe(WIRE, 40);
		assertEquals(40, process.total());
		assertEquals(40, runOperation(process, new Fuel(8)));
	}

	@Test
	void loadedProgressSurvivesFirstLookup() {
		MachineProcess process = new MachineProcess();
		process.load(7, 50, WIRE);
		process.setRecipe(WIRE, 100);
		assertEquals(50, process.progress());
		assertEquals(100, process.total());
		assertEquals(TickResult.WORKING, process.tick(true, () -> 0));
		assertEquals(51, process.progress());
		process.setRecipe(COIL, 100);
		assertEquals(0, process.progress());
	}
}
