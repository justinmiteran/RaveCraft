package com.ravecraft.mod.core.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TrackTableTest {
	@Test
	void putThenGet() {
		TrackTable table = new TrackTable();
		UUID id = UUID.randomUUID();
		table.put(id, "C:/music/a.mp3");
		assertEquals(Optional.of("C:/music/a.mp3"), table.get(id));
		assertTrue(table.get(UUID.randomUUID()).isEmpty());
	}

	@Test
	void evictsOldestPast5000() {
		TrackTable table = new TrackTable();
		List<UUID> ids = new ArrayList<>();
		for (int i = 0; i <= TrackTable.MAX_ENTRIES; i++) {
			UUID id = UUID.randomUUID();
			ids.add(id);
			table.put(id, "p" + i);
		}
		assertEquals(5000, TrackTable.MAX_ENTRIES);
		assertTrue(table.get(ids.getFirst()).isEmpty());
		assertEquals(Optional.of("p5000"), table.get(ids.getLast()));
		assertEquals(5000, table.snapshot().size());
	}

	@Test
	void ofKeepsOrderAndCaps() {
		Map<UUID, String> ordered = new LinkedHashMap<>();
		for (int i = 0; i < 5005; i++) {
			ordered.put(UUID.randomUUID(), "p" + i);
		}
		TrackTable table = TrackTable.of(ordered);
		List<String> paths = new ArrayList<>(table.snapshot().values());
		assertEquals(5000, paths.size());
		assertEquals("p5", paths.getFirst());
		assertEquals("p5004", paths.getLast());
	}

	@Test
	void snapshotRoundTripsThroughOf() {
		TrackTable table = new TrackTable();
		table.put(UUID.randomUUID(), "a");
		table.put(UUID.randomUUID(), "b");
		assertEquals(new ArrayList<>(table.snapshot().entrySet()),
				new ArrayList<>(TrackTable.of(table.snapshot()).snapshot().entrySet()));
	}
}
