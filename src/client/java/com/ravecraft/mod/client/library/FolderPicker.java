package com.ravecraft.mod.client.library;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/**
 * Opens the operating system's native folder dialog off the render thread.
 *
 * <p>At most one dialog is open at a time: a second call while one is pending returns the pending
 * future. Callers must hop back to the client thread with {@code Minecraft.getInstance().execute}.
 */
public final class FolderPicker {

	/** Outcome of a folder pick. */
	public sealed interface PickResult {
		record Chosen(Path folder) implements PickResult {}

		record Cancelled() implements PickResult {}

		record Unavailable(String reason) implements PickResult {}
	}

	private static final long TIMEOUT_MINUTES = 5;

	private static CompletableFuture<PickResult> pending;

	private FolderPicker() {}

	public static synchronized CompletableFuture<PickResult> pick(String title, Path start) {
		if (pending != null && !pending.isDone()) {
			return pending;
		}
		CompletableFuture<PickResult> future = new CompletableFuture<>();
		pending = future;
		Thread thread = new Thread(() -> {
			try {
				String chosen = TinyFileDialogs.tinyfd_selectFolderDialog(title, start.toString());
				future.complete(chosen == null ? new PickResult.Cancelled() : new PickResult.Chosen(Path.of(chosen)));
			} catch (Throwable t) {
				future.complete(new PickResult.Unavailable(String.valueOf(t.getMessage())));
			}
		}, "ravecraft-folder-picker");
		thread.setDaemon(true);
		thread.start();
		// The dialog thread may stay blocked; the caller is released with a timeout result.
		return pending = future.completeOnTimeout(new PickResult.Unavailable("timeout"), TIMEOUT_MINUTES, TimeUnit.MINUTES);
	}
}
