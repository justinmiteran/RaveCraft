package com.ravecraft.mod.client.library;

import com.ravecraft.mod.RaveCraft;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The modern Windows folder dialog (the Explorer-style one, which follows the system's dark mode), shown by a
 * PowerShell helper since Java cannot call it directly. The helper script ships as
 * {@code /ravecraft/folder_dialog.ps1}.
 *
 * <p>Starting PowerShell and compiling the interop takes about a second, so {@link #warmUp} starts a helper ahead of
 * time (when the Folders popup opens) and keeps it waiting; {@link #pick} uses it, and {@link #coolDown} stops it.
 * All methods may be called from any thread.
 */
final class WindowsFolderDialog {
	private static final String SCRIPT = "/ravecraft/folder_dialog.ps1";
	private static final Object LOCK = new Object();
	/** A started helper waiting for a request, if any. */
	private static @Nullable Helper idle;
	/** True between {@link #warmUp} and {@link #coolDown}: a helper is kept waiting. */
	private static boolean wanted;
	private static boolean starting;

	private WindowsFolderDialog() {}

	static boolean supported() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
	}

	/** Starts a helper in the background, unless one is already waiting or starting. */
	static void warmUp() {
		if (!supported()) {
			return;
		}
		synchronized (LOCK) {
			wanted = true;
			if (starting || idle != null && idle.alive()) {
				return;
			}
			starting = true;
		}
		Thread thread = new Thread(() -> {
			Helper helper = null;
			try {
				helper = Helper.start();
			} catch (IOException | RuntimeException e) {
				RaveCraft.LOGGER.debug("Could not start the folder dialog helper", e);
			}
			synchronized (LOCK) {
				starting = false;
				if (helper != null && wanted && idle == null) {
					idle = helper;
					helper = null;
				}
			}
			if (helper != null) {
				helper.close();
			}
		}, "ravecraft-folder-dialog-warmup");
		thread.setDaemon(true);
		thread.start();
	}

	/** Stops the waiting helper; a dialog already open stays open until the player closes it. */
	static void coolDown() {
		Helper helper;
		synchronized (LOCK) {
			wanted = false;
			helper = idle;
			idle = null;
		}
		if (helper != null) {
			helper.close();
		}
	}

	/**
	 * Blocks until the dialog closes: the chosen folder, or empty when the player cancelled. Uses the waiting helper,
	 * or starts one. Throws when the dialog could not be shown, so the caller can fall back to another one.
	 */
	static Optional<Path> pick(String title, @Nullable String start) throws IOException {
		Helper helper;
		synchronized (LOCK) {
			helper = idle;
			idle = null;
		}
		if (helper == null || !helper.alive()) {
			if (helper != null) {
				helper.close();
			}
			helper = Helper.start();
		}
		try {
			Optional<Path> chosen = helper.pick(title, start != null ? start : "");
			synchronized (LOCK) {
				if (wanted && idle == null) {
					idle = helper; // kept for the next click
					helper = null;
				}
			}
			return chosen;
		} finally {
			if (helper != null) {
				helper.close();
			}
		}
	}

	/** One PowerShell process running the script: it answers one request at a time until its stdin closes. */
	private record Helper(Process process, BufferedWriter in, BufferedReader out) {
		static Helper start() throws IOException {
			ProcessBuilder builder = new ProcessBuilder(powershell(), "-NoProfile", "-NonInteractive", "-Sta",
					"-ExecutionPolicy", "Bypass", "-EncodedCommand", encodedScript());
			builder.redirectError(ProcessBuilder.Redirect.DISCARD);
			Process process = builder.start();
			return new Helper(process,
					new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)),
					new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)));
		}

		boolean alive() {
			return process.isAlive();
		}

		/** Asks the helper for a folder; empty when the player cancelled. */
		Optional<Path> pick(String title, String start) throws IOException {
			in.write(oneLine(title) + "\n" + oneLine(start) + "\n");
			in.flush();
			String answer = out.readLine();
			if (answer == null) {
				throw new IOException("folder dialog helper stopped");
			}
			answer = answer.replace("﻿", "");
			if (answer.equals("0")) {
				return Optional.empty();
			}
			if (answer.length() > 1 && answer.charAt(0) == '1') {
				return Optional.of(Path.of(answer.substring(1)));
			}
			throw new IOException("unexpected folder dialog answer");
		}

		void close() {
			try {
				in.close();
			} catch (IOException e) {
				RaveCraft.LOGGER.debug("Folder dialog helper already gone: {}", e.toString());
			}
			process.destroy();
		}

		private static String oneLine(String text) {
			return text.replace('\r', ' ').replace('\n', ' ');
		}
	}

	private static String powershell() {
		String root = System.getenv("SystemRoot");
		if (root != null) {
			Path exe = Path.of(root, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
			if (Files.isRegularFile(exe)) {
				return exe.toString();
			}
		}
		return "powershell.exe";
	}

	/** The script as {@code -EncodedCommand} wants it: Base64 of its UTF-16LE text. */
	private static String encodedScript() throws IOException {
		try (InputStream in = WindowsFolderDialog.class.getResourceAsStream(SCRIPT)) {
			if (in == null) {
				throw new IOException("missing " + SCRIPT);
			}
			String script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			return Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
		}
	}
}
