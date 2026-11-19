package com.ravecraft.mod.client.library;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.core.library.SingleFlight;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/**
 * Opens the operating system's native folder dialog off the render thread: on Windows the modern Explorer-style
 * one (see {@link WindowsFolderDialog}), elsewhere or when it fails the one tinyfd offers.
 *
 * <p>At most one dialog is open at a time: a call while one is still up returns the pending future, which
 * completes when that dialog closes. There is no timeout, since a dialog cannot be closed from here. Callers must
 * hop back to the client thread with {@code Minecraft.getInstance().execute}.
 */
public final class FolderPicker {

	/** Outcome of a folder pick. */
	public sealed interface PickResult {
		record Chosen(Path folder) implements PickResult {}

		record Cancelled() implements PickResult {}

		record Unavailable(String reason) implements PickResult {}
	}

	private static final SingleFlight<PickResult> DIALOG = new SingleFlight<>();

	private FolderPicker() {}

	/** Opens the dialog on {@code start}, or on the user's home folder when it is null. */
	public static CompletableFuture<PickResult> pick(String title, @Nullable Path start) {
		String defaultPath = start != null ? start.toString() : System.getProperty("user.home");
		return DIALOG.run(() -> {
			if (WindowsFolderDialog.supported()) {
				try {
					return WindowsFolderDialog.pick(title, defaultPath)
							.<PickResult>map(PickResult.Chosen::new)
							.orElseGet(PickResult.Cancelled::new);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return new PickResult.Cancelled();
				} catch (IOException | RuntimeException e) {
					RaveCraft.LOGGER.warn("Modern folder dialog unavailable, using the classic one", e);
				}
			}
			String chosen = TinyFileDialogs.tinyfd_selectFolderDialog(title, defaultPath);
			return chosen == null ? new PickResult.Cancelled() : new PickResult.Chosen(Path.of(chosen));
		}, FolderPicker::unavailable, task -> {
			Thread thread = new Thread(task, "ravecraft-folder-picker");
			thread.setDaemon(true);
			thread.start();
		});
	}

	private static PickResult unavailable(Throwable t) {
		return new PickResult.Unavailable(t.getMessage() != null ? t.getMessage() : t.toString());
	}
}
