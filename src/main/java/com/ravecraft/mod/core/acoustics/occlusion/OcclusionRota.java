package com.ravecraft.mod.core.acoustics.occlusion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which streams get their occlusion traced on a tick: each stream is refreshed every {@value #PERIOD} ticks, on a
 * slot handed out round-robin when it is first seen. A new stream is traced at once, and a stream whose trace ran out
 * of budget goes first on the next tick. Not thread-safe.
 */
public final class OcclusionRota {
	/** Ticks between two traces of the same stream. */
	public static final int PERIOD = 5;

	private final Map<Integer, Integer> slots = new HashMap<>();
	private final Set<Integer> unfinished = new LinkedHashSet<>();
	private int nextSlot;

	/**
	 * The ids due on {@code tick}, among {@code audibleByGain} (loudest first): the ids never picked before, then those
	 * left unfinished, then those whose slot is {@code tick % PERIOD}, each in that list's order.
	 *
	 * @param audibleByGain the audible ids, loudest first
	 * @param tick the current tick
	 * @return the ids to trace this tick
	 */
	public List<Integer> pick(List<Integer> audibleByGain, long tick) {
		Set<Integer> due = new LinkedHashSet<>();
		for (Integer id : audibleByGain) {
			if (!slots.containsKey(id)) {
				slots.put(id, nextSlot);
				nextSlot = (nextSlot + 1) % PERIOD;
				due.add(id);
			}
		}
		Set<Integer> audible = Set.copyOf(audibleByGain);
		for (Integer id : unfinished) {
			if (audible.contains(id)) {
				due.add(id);
			}
		}
		unfinished.clear();
		int slot = (int) Math.floorMod(tick, (long) PERIOD);
		for (Integer id : audibleByGain) {
			if (slots.get(id) == slot) {
				due.add(id);
			}
		}
		return new ArrayList<>(due);
	}

	/**
	 * The trace of {@code id} did not finish this tick: it goes first on the next one.
	 *
	 * @param id the stream's id
	 */
	public void unfinished(int id) {
		unfinished.add(id);
	}

	/**
	 * Drops every id not in {@code alive}; one that comes back later counts as new.
	 *
	 * @param alive the ids still audible
	 */
	public void forget(Set<Integer> alive) {
		slots.keySet().retainAll(alive);
		unfinished.retainAll(alive);
	}
}
