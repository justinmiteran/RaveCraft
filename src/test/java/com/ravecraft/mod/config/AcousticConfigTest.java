package com.ravecraft.mod.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ravecraft.mod.core.acoustics.AcousticSettings;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AcousticConfigTest {
	@TempDir
	Path dir;

	@AfterEach
	void restore() {
		AcousticConfig.set(AcousticSettings.DEFAULT);
	}

	@Test
	void rewritesIncompleteFileWithBackup() throws IOException {
		Path file = dir.resolve(AcousticConfig.FILE_NAME);
		String old = "{ \"airAbsorptionDbPerBlock\": 0.1 }";
		Files.writeString(file, old, StandardCharsets.UTF_8);
		AcousticConfig.load(file);
		assertEquals(old, Files.readString(dir.resolve(AcousticConfig.FILE_NAME + ".bak"), StandardCharsets.UTF_8));
		String text = Files.readString(file, StandardCharsets.UTF_8);
		assertTrue(text.contains("\"occlusion\":"), text);
		assertTrue(text.contains("\"air\":") && text.contains("\"highLossDbPerBlock\": 0.1"), text);
		assertEquals(0.1, AcousticConfig.get().airAbsorptionDbPerBlock());
		AcousticSettingsJson.Result reread = AcousticSettingsJson.read(new java.io.StringReader(text), w -> { });
		assertEquals(0.1, reread.settings().occlusion().air().highLossDbPerBlock());
		assertTrue(reread.complete());
	}

	@Test
	void leavesBrokenFileAlone() throws IOException {
		for (String broken : new String[] {"{ \"rolloff\": ", "{ \"rolloff\": \"fast\" }"}) {
			Path file = dir.resolve(AcousticConfig.FILE_NAME);
			Files.writeString(file, broken, StandardCharsets.UTF_8);
			byte[] before = Files.readAllBytes(file);
			AcousticConfig.load(file);
			assertFalse(Files.exists(dir.resolve(AcousticConfig.FILE_NAME + ".bak")));
			assertArrayEquals(before, Files.readAllBytes(file));
			assertEquals(AcousticSettings.DEFAULT, AcousticConfig.get());
		}
	}

	@Test
	void leavesCompleteFileAlone() throws IOException {
		Path file = dir.resolve(AcousticConfig.FILE_NAME);
		StringWriter out = new StringWriter();
		AcousticSettingsJson.write(AcousticSettings.DEFAULT, out);
		Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
		byte[] before = Files.readAllBytes(file);
		AcousticConfig.load(file);
		assertFalse(Files.exists(dir.resolve(AcousticConfig.FILE_NAME + ".bak")));
		assertArrayEquals(before, Files.readAllBytes(file));
	}
}
