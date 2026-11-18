package com.ravecraft.mod.core.acoustics.occlusion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class OcclusionRotaTest {
	@Test
	void newStreamsFirst() {
		OcclusionRota rota = new OcclusionRota();
		rota.pick(List.of(1, 2), 0);
		List<Integer> picked = rota.pick(List.of(1, 2, 3), 1);
		assertEquals(3, picked.getFirst());
	}

	@Test
	void eachStreamEveryFifthTick() {
		OcclusionRota rota = new OcclusionRota();
		List<Integer> ids = IntStream.range(0, 10).boxed().toList();
		assertEquals(ids, rota.pick(ids, 0));
		Map<Integer, Integer> counts = new HashMap<>();
		for (long tick = 1; tick <= 5; tick++) {
			for (int id : rota.pick(ids, tick)) {
				counts.merge(id, 1, Integer::sum);
			}
		}
		for (int id : ids) {
			assertEquals(1, counts.getOrDefault(id, 0), "id " + id);
		}
	}

	@Test
	void rotaPutsUnfinishedFirst() {
		OcclusionRota rota = new OcclusionRota();
		List<Integer> ids = IntStream.range(0, 10).boxed().toList();
		rota.pick(ids, 0);
		// Slots are assigned round-robin from 0: ids 2 and 7 share slot 2, picked on tick 2.
		rota.unfinished(7);
		List<Integer> picked = rota.pick(ids, 1);
		assertEquals(7, picked.getFirst());
		assertTrue(picked.contains(1));
		assertTrue(picked.contains(6));
		// Once rescheduled, it waits for its slot again.
		assertFalse(rota.pick(ids, 1).contains(7));
		assertTrue(rota.pick(ids, 2).contains(7));
	}

	@Test
	void forgetsDroppedStreams() {
		OcclusionRota rota = new OcclusionRota();
		rota.pick(List.of(1, 2), 0);
		rota.unfinished(2);
		rota.forget(Set.of(1));
		// 2 is no longer known: it comes back as a new stream, picked first.
		List<Integer> picked = rota.pick(List.of(1, 2), 3);
		assertEquals(List.of(2), picked);
	}

	@Test
	void forgottenThenSeenAgainIsPickedAtOnce() {
		OcclusionRota rota = new OcclusionRota();
		rota.pick(List.of(1, 2), 0);
		// Id 1 has slot 0: it would wait until tick 5.
		assertFalse(rota.pick(List.of(1, 2), 1).contains(1));
		rota.forget(Set.of(2));
		assertEquals(1, rota.pick(List.of(1, 2), 2).getFirst());
	}

	@Test
	void unfinishedNoLongerAudibleIsNotReturned() {
		OcclusionRota rota = new OcclusionRota();
		rota.pick(List.of(1, 2), 0);
		rota.unfinished(2);
		assertFalse(rota.pick(List.of(1), 2).contains(2));
	}
}
