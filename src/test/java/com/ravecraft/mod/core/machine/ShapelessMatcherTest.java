package com.ravecraft.mod.core.machine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ShapelessMatcherTest {
	private record Ing(String item, int count) {
	}

	private record Stack(String item, int count) {
		static final Stack EMPTY = new Stack("", 0);
	}

	private static Optional<int[]> match(List<Ing> ingredients, Stack... slots) {
		return ShapelessMatcher.match(ingredients, Ing::count, (ing, slot) -> ing.item().equals(slot.item()),
				Arrays.asList(slots), slot -> slot.count() == 0, Stack::count);
	}

	@Test
	void matchesInAnyOrder() {
		List<Ing> recipe = List.of(new Ing("a", 1), new Ing("b", 2));
		assertArrayEquals(new int[] {2, 0}, match(recipe, new Stack("b", 2), Stack.EMPTY, new Stack("a", 5)).orElseThrow());
	}

	@Test
	void rejectsInsufficientCount() {
		assertTrue(match(List.of(new Ing("wire", 4)), new Stack("wire", 3), Stack.EMPTY, Stack.EMPTY).isEmpty());
	}

	@Test
	void rejectsExtraNonEmptySlot() {
		assertTrue(match(List.of(new Ing("a", 1)), new Stack("a", 1), new Stack("x", 1), Stack.EMPTY).isEmpty());
	}

	@Test
	void rejectsTwoIngredientsInOneSlot() {
		List<Ing> recipe = List.of(new Ing("a", 1), new Ing("a", 1));
		assertTrue(match(recipe, new Stack("a", 2), Stack.EMPTY, Stack.EMPTY).isEmpty());
		assertArrayEquals(new int[] {0, 1}, match(recipe, new Stack("a", 1), new Stack("a", 1), Stack.EMPTY)
				.orElseThrow());
	}

	@Test
	void matchesSingleIngredientInAnySlot() {
		List<Ing> recipe = List.of(new Ing("copper", 1));
		assertArrayEquals(new int[] {1}, match(recipe, Stack.EMPTY, new Stack("copper", 1), Stack.EMPTY).orElseThrow());
	}

	@Test
	void backtracksWhenFirstChoiceBlocksAnother() {
		// "any" fits both slots, "b" only the second: the greedy first choice must be undone.
		List<Ing> recipe = List.of(new Ing("any", 1), new Ing("b", 1));
		Optional<int[]> result = ShapelessMatcher.match(recipe, Ing::count,
				(ing, slot) -> "any".equals(ing.item()) || ing.item().equals(slot.item()),
				List.of(new Stack("b", 1), new Stack("c", 1)), slot -> slot.count() == 0, Stack::count);
		assertArrayEquals(new int[] {1, 0}, result.orElseThrow());
	}

	@Test
	void rejectsTooManyIngredients() {
		List<Ing> recipe = List.of(new Ing("a", 1), new Ing("b", 1), new Ing("c", 1), new Ing("d", 1));
		assertTrue(match(recipe, new Stack("a", 1), new Stack("b", 1), new Stack("c", 1), new Stack("d", 1)).isEmpty());
	}
}
