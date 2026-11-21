package com.ravecraft.mod.client.library;

import com.ravecraft.mod.RaveCraft;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

/** Runs disk work for the screens on {@link Util#ioPool()} and hands the result back to the client thread. */
public final class OffThread {
	private OffThread() {
	}

	/** Runs {@code task} on the IO pool, then {@code apply} (or {@code failed}, after logging) on the client thread. */
	public static <T> void run(Callable<T> task, Consumer<T> apply, Runnable failed) {
		run(Util.ioPool(), task, apply, failed);
	}

	/** Same, on {@code executor}. */
	public static <T> void run(Executor executor, Callable<T> task, Consumer<T> apply, Runnable failed) {
		CompletableFuture.supplyAsync(() -> {
			try {
				return task.call();
			} catch (Exception e) {
				throw new CompletionException(e);
			}
		}, executor).whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
			if (error == null) {
				apply.accept(result);
			} else {
				RaveCraft.LOGGER.warn("Could not read the music library", error);
				failed.run();
			}
		}));
	}
}
