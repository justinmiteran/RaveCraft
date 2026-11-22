package com.ravecraft.mod.core.library;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Runs at most one blocking task at a time, such as a native dialog. While a task runs, {@link #run} returns the
 * pending future instead of starting another one. The flight is freed only when the task itself returns (never on
 * a timeout), so a task that blocks for a long time keeps the next one from stacking up behind it.
 */
public final class SingleFlight<T> {
	private final AtomicBoolean busy = new AtomicBoolean();
	private CompletableFuture<T> pending = CompletableFuture.completedFuture(null);

	/**
	 * Starts {@code task} through {@code launcher} (for example on a new thread), or returns the pending future
	 * when a task is still running. A task or launcher that throws completes the future with {@code onError}.
	 *
	 * @param task the work to run
	 * @param onError turns a failure into a result
	 * @param launcher starts the work
	 * @return the future result
	 */
	public synchronized CompletableFuture<T> run(Supplier<T> task, Function<Throwable, T> onError,
			Consumer<Runnable> launcher) {
		if (!busy.compareAndSet(false, true)) {
			return pending;
		}
		CompletableFuture<T> future = new CompletableFuture<>();
		pending = future;
		try {
			launcher.accept(() -> complete(future, task, onError));
		} catch (Throwable t) {
			busy.set(false);
			future.complete(onError.apply(t));
		}
		return future;
	}

	/** Runs {@code task}, frees the slot, then completes {@code future} with its result or {@code onError}'s. */
	private void complete(CompletableFuture<T> future, Supplier<T> task, Function<Throwable, T> onError) {
		T result;
		try {
			result = task.get();
		} catch (Throwable t) {
			result = onError.apply(t);
		} finally {
			busy.set(false);
		}
		future.complete(result);
	}

	/**
	 * True while a task runs.
	 *
	 * @return true while a task runs
	 */
	public boolean busy() {
		return busy.get();
	}
}
