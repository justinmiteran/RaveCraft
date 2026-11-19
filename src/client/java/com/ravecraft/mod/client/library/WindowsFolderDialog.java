package com.ravecraft.mod.client.library;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The modern Windows folder dialog (the Explorer-style one, which follows the system's dark mode), run through
 * PowerShell since Java cannot call it directly. The script ships as {@code /ravecraft/folder_dialog.ps1}.
 */
final class WindowsFolderDialog {
	private static final String SCRIPT = "/ravecraft/folder_dialog.ps1";

	private WindowsFolderDialog() {}

	static boolean supported() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
	}

	/**
	 * Blocks until the dialog closes: the chosen folder, or empty when the player cancelled. Throws when the dialog
	 * could not be shown, so the caller can fall back to another one.
	 */
	static Optional<Path> pick(String title, @Nullable String start) throws IOException, InterruptedException {
		ProcessBuilder builder = new ProcessBuilder(powershell(), "-NoProfile", "-NonInteractive", "-Sta",
				"-ExecutionPolicy", "Bypass", "-EncodedCommand", encodedScript());
		builder.environment().put("RAVECRAFT_DIALOG_TITLE", title);
		builder.environment().put("RAVECRAFT_DIALOG_START", start != null ? start : "");
		builder.redirectError(ProcessBuilder.Redirect.DISCARD);
		Process process = builder.start();
		process.getOutputStream().close();
		String out;
		try (InputStream stdout = process.getInputStream()) {
			out = new String(stdout.readAllBytes(), StandardCharsets.UTF_8).trim();
		}
		int code = process.waitFor();
		if (code == 1) {
			return Optional.empty();
		}
		if (code != 0 || out.isEmpty()) {
			throw new IOException("folder dialog failed (exit code " + code + ")");
		}
		return Optional.of(Path.of(out));
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
