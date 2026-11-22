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
		/** The player chose {@code folder}. */
		record Chosen(Path folder) implements PickResult {}

		/** The player closed the dialog. */
		record Cancelled() implements PickResult {}

		/** No dialog could open, for {@code reason}. */
		record Unavailable(String reason) implements PickResult {}
	}

	private static final SingleFlight<PickResult> DIALOG = new SingleFlight<>();

	private FolderPicker() {}

	/** Gets the dialog ready in the background so the next {@link #pick} opens it at once (Windows only). */
	public static void warmUp() {
		WindowsFolderDialog.warmUp();
	}

	/** Lets go of what {@link #warmUp} prepared. */
	public static void coolDown() {
		WindowsFolderDialog.coolDown();
	}

	/**
	 * For testing the fallbacks: {@code -Dravecraft.folderDialog=builtin} reports the system dialog unavailable, so
	 * the in-game browser opens; {@code classic} skips the modern Windows dialog for tinyfd's.
	 */
	private static final String FORCED = System.getProperty("ravecraft.folderDialog", "");

	/**
	 * Opens the dialog on {@code start}, or on the user's home folder when it is null.
	 *
	 * @param title the dialog's title
	 * @param start the folder to open on, or null
	 * @return the folder chosen, completed off the client thread
	 */
	public static CompletableFuture<PickResult> pick(String title, @Nullable Path start) {
		if (FORCED.equals("builtin")) {
			return CompletableFuture.completedFuture(new PickResult.Unavailable("forced by -Dravecraft.folderDialog"));
		}
		String defaultPath = start != null ? start.toString() : System.getProperty("user.home");
		return DIALOG.run(() -> {
			if (WindowsFolderDialog.supported() && !FORCED.equals("classic")) {
				try {
					return WindowsFolderDialog.pick(title, defaultPath)
							.<PickResult>map(PickResult.Chosen::new)
							.orElseGet(PickResult.Cancelled::new);
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
