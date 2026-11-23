package com.ravecraft.mod.core.machine;

import java.util.Objects;
import java.util.function.IntSupplier;
import org.jspecify.annotations.Nullable;

/**
 * Progress and energy of one machine. An operation costs one energy unit when it starts, takes the recipe's duration,
 * waits at its end while the output is full, and restarts from zero when the recipe changes. Not thread-safe.
 */
public final class MachineProcess {
	/** What a tick did. */
	public enum TickResult {
		/** No recipe matches the inputs. */
		IDLE,
		/** An operation should start but there is no energy. */
		WAITING_ENERGY,
		/** The operation is over but the output cannot take the result. */
		BLOCKED,
		/** The operation advanced. */
		WORKING,
		/** The operation finished: produce the result once, now. */
		DONE
	}

	private @Nullable Object recipe;
	private int total;
	private int progress;
	private int energy;

	/**
	 * Sets the recipe the inputs currently match. The same recipe keeps the progress (and takes the given duration, which
	 * completes a {@link #load}); another one, or none, resets it.
	 *
	 * @param recipeKey an identity of the recipe, or null when nothing matches
	 * @param processingTicks the recipe's duration, in ticks
	 */
	public void setRecipe(@Nullable Object recipeKey, int processingTicks) {
		total = recipeKey == null ? 0 : Math.max(1, processingTicks);
		if (!Objects.equals(recipeKey, recipe)) {
			recipe = recipeKey;
			progress = 0;
		}
	}

	/**
	 * Advances the machine by one tick.
	 *
	 * @param outputFits whether the output slot can take the result now
	 * @param refuel consumes one energy item and returns its units (0 when there is none); called only when an
	 *     operation must start with an empty reserve
	 * @return what the tick did
	 */
	public TickResult tick(boolean outputFits, IntSupplier refuel) {
		if (recipe == null) {
			return TickResult.IDLE;
		}
		if (progress == 0 && !start(refuel)) {
			return TickResult.WAITING_ENERGY;
		}
		return advance(outputFits);
	}

	/** Advances a started operation; at its end, waits for room in the output or completes it. */
	private TickResult advance(boolean outputFits) {
		if (progress < total) {
			progress++;
		}
		if (progress < total) {
			return TickResult.WORKING;
		}
		if (!outputFits) {
			return TickResult.BLOCKED;
		}
		progress = 0;
		return TickResult.DONE;
	}

	private boolean start(IntSupplier refuel) {
		if (energy == 0) {
			energy = Math.min(MachineConstants.MAX_ENERGY, Math.max(0, refuel.getAsInt()));
		}
		if (energy == 0) {
			return false;
		}
		energy--;
		return true;
	}

	/**
	 * Restores saved values, clamped to their valid ranges. The duration stays unknown until the next
	 * {@link #setRecipe}, which keeps the progress if it names the same recipe.
	 *
	 * @param savedEnergy the energy reserve
	 * @param savedProgress the progress of the current operation, in ticks
	 * @param savedRecipe the identity of the recipe in progress, or null
	 */
	public void load(int savedEnergy, int savedProgress, @Nullable Object savedRecipe) {
		recipe = savedRecipe;
		energy = Math.clamp(savedEnergy, 0, MachineConstants.MAX_ENERGY);
		progress = Math.max(0, savedProgress);
	}

	/**
	 * Returns the energy reserve, in operations.
	 *
	 * @return the energy reserve, in operations
	 */
	public int energy() {
		return energy;
	}

	/**
	 * Returns the ticks done in the current operation.
	 *
	 * @return the ticks done in the current operation
	 */
	public int progress() {
		return progress;
	}

	/**
	 * Returns the duration of the current recipe, in ticks (0 without a recipe).
	 *
	 * @return the duration of the current recipe, in ticks (0 without a recipe)
	 */
	public int total() {
		return total;
	}

	/**
	 * Returns whether an operation is under way.
	 *
	 * @return whether an operation is under way
	 */
	public boolean running() {
		return progress > 0;
	}
}
