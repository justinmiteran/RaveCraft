package com.ravecraft.mod.core.machine;

import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Matches a shapeless machine recipe against the input slots: each ingredient takes its own slot holding at least its
 * count, and every other slot must be empty.
 */
public final class ShapelessMatcher {
	private ShapelessMatcher() {
	}

	/**
	 * Finds the slot of each ingredient.
	 *
	 * @param ingredients the recipe's ingredients, at most {@link MachineConstants#MAX_INGREDIENTS}
	 * @param count the count an ingredient needs
	 * @param test whether a slot's item is the ingredient
	 * @param slots the input slots
	 * @param isEmpty whether a slot is empty
	 * @param slotCount the item count of a slot
	 * @param <I> the ingredient type
	 * @param <S> the slot content type
	 * @return for each ingredient the index of its slot, or empty when the slots do not match
	 */
	public static <I, S> Optional<int[]> match(List<I> ingredients, ToIntFunction<I> count, BiPredicate<I, S> test,
			List<S> slots, Predicate<S> isEmpty, ToIntFunction<S> slotCount) {
		if (ingredients.isEmpty() || ingredients.size() > MachineConstants.MAX_INGREDIENTS
				|| ingredients.size() > slots.size()) {
			return Optional.empty();
		}
		Search<I, S> search = new Search<>(ingredients, count, test, slots, isEmpty, slotCount);
		return search.assign(0) ? Optional.of(search.assignment) : Optional.empty();
	}

	/** Backtracking over the ingredient-to-slot assignments (at most 3! orders). */
	private static final class Search<I, S> {
		private final List<I> ingredients;
		private final ToIntFunction<I> count;
		private final BiPredicate<I, S> test;
		private final List<S> slots;
		private final Predicate<S> isEmpty;
		private final ToIntFunction<S> slotCount;
		private final int[] assignment;
		private final boolean[] used;

		Search(List<I> ingredients, ToIntFunction<I> count, BiPredicate<I, S> test, List<S> slots,
				Predicate<S> isEmpty, ToIntFunction<S> slotCount) {
			this.ingredients = ingredients;
			this.count = count;
			this.test = test;
			this.slots = slots;
			this.isEmpty = isEmpty;
			this.slotCount = slotCount;
			this.assignment = new int[ingredients.size()];
			this.used = new boolean[slots.size()];
		}

		/**
		 * Assigns the ingredients from {@code index} on, undoing a choice that leaves no assignment for the rest.
		 *
		 * @param index the first ingredient still unassigned
		 * @return whether every ingredient got a slot and the other slots are empty
		 */
		boolean assign(int index) {
			if (index == ingredients.size()) {
				return othersEmpty();
			}
			for (int slot = 0; slot < slots.size(); slot++) {
				if (!used[slot] && fits(ingredients.get(index), slots.get(slot))) {
					used[slot] = true;
					assignment[index] = slot;
					if (assign(index + 1)) {
						return true;
					}
					used[slot] = false;
				}
			}
			return false;
		}

		private boolean fits(I ingredient, S slot) {
			return !isEmpty.test(slot) && test.test(ingredient, slot)
					&& slotCount.applyAsInt(slot) >= count.applyAsInt(ingredient);
		}

		private boolean othersEmpty() {
			for (int slot = 0; slot < slots.size(); slot++) {
				if (!used[slot] && !isEmpty.test(slots.get(slot))) {
					return false;
				}
			}
			return true;
		}
	}
}
