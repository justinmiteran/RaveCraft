package com.ravecraft.mod.core.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class SingleFlightTest {
	/** Keeps the launched tasks instead of running them, so the test decides when the "dialog" returns. */
	private final List<Runnable> launched = new ArrayList<>();
	private final Consumer<Runnable> launcher = launched::add;
	private final SingleFlight<String> flight = new SingleFlight<>();

	@Test
	void runsTheTaskAndCompletesWithItsResult() {
		CompletableFuture<String> future = flight.run(() -> "chosen", Throwable::toString, launcher);
		assertTrue(flight.busy());
		assertFalse(future.isDone());
		launched.get(0).run();
		assertEquals("chosen", future.join());
		assertFalse(flight.busy());
	}

	@Test
	void aSecondRunWhileBusyReturnsThePendingFutureWithoutLaunching() {
		CompletableFuture<String> first = flight.run(() -> "a", Throwable::toString, launcher);
		CompletableFuture<String> second = flight.run(() -> "b", Throwable::toString, launcher);
		assertSame(first, second);
		assertEquals(1, launched.size());
		launched.get(0).run();
		assertEquals("a", second.join());
	}

	@Test
	void aRunAfterTheTaskReturnedLaunchesAgain() {
		CompletableFuture<String> first = flight.run(() -> "a", Throwable::toString, launcher);
		launched.get(0).run();
		CompletableFuture<String> second = flight.run(() -> "b", Throwable::toString, launcher);
		assertNotSame(first, second);
		assertEquals(2, launched.size());
		launched.get(1).run();
		assertEquals("b", second.join());
	}

	@Test
	void aFailingTaskCompletesWithTheErrorResultAndFreesTheFlight() {
		CompletableFuture<String> future = flight.run(() -> {
			throw new IllegalStateException("no display");
		}, t -> "failed: " + t.getMessage(), launcher);
		launched.get(0).run();
		assertEquals("failed: no display", future.join());
		assertFalse(flight.busy());
	}

	@Test
	void aLauncherThatCannotStartFreesTheFlight() {
		CompletableFuture<String> future = flight.run(() -> "a", t -> "failed", task -> {
			throw new IllegalStateException("no thread");
		});
		assertEquals("failed", future.join());
		assertFalse(flight.busy());
	}
}
