package com.ravecraft.mod.core.audio.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ravecraft.mod.core.audio.stream.SessionTable.Opened;
import com.ravecraft.mod.core.audio.stream.SessionTable.Session;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionTableTest {
	private final SessionTable<String> table = new SessionTable<>();
	private final UUID alice = UUID.randomUUID();
	private final UUID bob = UUID.randomUUID();

	@Test
	void openCreatesSessionWithFreshId() {
		Opened<String> opened = table.open("a", alice);
		Session<String> session = opened.session();
		assertTrue(session.id() > 0);
		assertEquals("a", session.key());
		assertEquals(alice, session.owner());
		assertTrue(opened.replaced().isEmpty());
		assertEquals(session, table.byId(session.id()).orElseThrow());
		assertEquals(session, table.byKey("a").orElseThrow());
	}

	@Test
	void openReplacesExistingSession() {
		Session<String> first = table.open("a", alice).session();
		Opened<String> second = table.open("a", bob);
		assertEquals(first, second.replaced().orElseThrow());
		assertTrue(table.byId(first.id()).isEmpty());
		assertEquals(second.session(), table.byKey("a").orElseThrow());
	}

	@Test
	void endByOwnerEndsOnlyTheirs() {
		Session<String> a = table.open("a", alice).session();
		Session<String> b = table.open("b", alice).session();
		Session<String> c = table.open("c", bob).session();
		List<Session<String>> ended = table.endByOwner(alice);
		assertEquals(2, ended.size());
		assertTrue(ended.contains(a) && ended.contains(b));
		assertTrue(table.byKey("a").isEmpty());
		assertEquals(c, table.byKey("c").orElseThrow());
	}

	@Test
	void endByIdOnUnknownIsEmpty() {
		assertTrue(table.endById(42).isEmpty());
		Session<String> a = table.open("a", alice).session();
		assertEquals(a, table.endById(a.id()).orElseThrow());
		assertTrue(table.byKey("a").isEmpty());
		assertTrue(table.endByKey("a").isEmpty());
	}

	@Test
	void idsNotReused() {
		Session<String> first = table.open("a", alice).session();
		table.endByKey("a");
		assertNotEquals(first.id(), table.open("a", alice).session().id());
	}

	@Test
	void endAllEmptiesTable() {
		table.open("a", alice);
		table.open("b", bob);
		assertEquals(2, table.endAll().size());
		assertTrue(table.byKey("b").isEmpty());
	}
}
